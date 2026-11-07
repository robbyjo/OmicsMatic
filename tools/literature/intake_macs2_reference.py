# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Retain exact official MACS2 sources and a private compatible Python archive."""
import argparse
import hashlib
import io
import json
import tarfile
import urllib.request
from pathlib import Path, PurePosixPath
from setup_deseq2_reference import ROOT, pin, save


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, required=True)
    out = parser.parse_args().out.resolve()
    base = ROOT / 'build/literature-review/signac-runtime'
    assert out.is_relative_to(base) and not out.exists()
    out.mkdir()
    downloads = []

    def fetch(url, name, maximum, digest=None):
        request = urllib.request.Request(url, headers={'User-Agent': 'OmicsMatic-source-validation/1.0'})
        with urllib.request.urlopen(request, timeout=90) as response:
            raw = response.read(maximum + 1)
            assert len(raw) <= maximum
            receipt = dict(url=url, final_url=response.geturl(), bytes=len(raw),
                           sha256=hashlib.sha256(raw).hexdigest())
        if digest is not None:
            assert receipt['sha256'] == digest, 'Published SHA256 differs'
        target = out / name
        target.write_bytes(raw)
        downloads.append(dict(path=name, **receipt, published_SHA256_checked=digest is not None))
        save(out / 'DOWNLOAD-PROGRESS.json', downloads)
        return raw

    try:
        editions = []
        for version in ['2.2.7.1', '2.2.9', '2.2.9.1']:
            metadata = json.loads(fetch('https://pypi.org/pypi/MACS2/' + version + '/json',
                                        'MACS2-' + version + '-pypi.json', 2_000_000))
            assert metadata['info']['version'] == version
            sources = [f for f in metadata['urls'] if f['packagetype'] == 'sdist']
            assert len(sources) == 1
            source = sources[0]
            raw = fetch(source['url'], source['filename'], 20_000_000, source['digests']['sha256'])
            folder = out / version
            folder.mkdir()
            total = 0
            with tarfile.open(fileobj=io.BytesIO(raw), mode='r:gz') as archive:
                members = archive.getmembers()
                assert len(members) <= 10000
                for member in members:
                    name = PurePosixPath(member.name)
                    assert not name.is_absolute() and '..' not in name.parts and '\\' not in member.name
                    assert name.parts[0].lower() == ('macs2-' + version).lower()
                    if member.isdir():
                        continue
                    assert member.isfile() and member.size <= 20_000_000
                    total += member.size
                    assert total <= 200_000_000
                    target = folder / Path(*name.parts[1:])
                    target.parent.mkdir(parents=True, exist_ok=True)
                    with archive.extractfile(member) as stream:
                        body = stream.read(member.size + 1)
                    assert len(body) == member.size
                    target.write_bytes(body)
            editions.append(dict(version=version, archive=pin(out / source['filename']),
                                 official_source_url=source['url'],
                                 files={p.relative_to(folder).as_posix(): pin(p) for p in folder.rglob('*') if p.is_file()}))
        release = json.loads(fetch('https://api.github.com/repos/astral-sh/python-build-standalone/releases/tags/20241016',
                                   'python-release.json', 8_000_000))
        assets = [a for a in release['assets'] if a['name'].startswith('cpython-3.11.') and
                  a['name'].endswith('-x86_64-unknown-linux-gnu-install_only.tar.gz')]
        assert len(assets) == 1, 'Require one exact private CPython3.11 asset'
        asset = assets[0]
        checksum = fetch(asset['browser_download_url'] + '.sha256', asset['name'] + '.sha256', 10000)
        digest = checksum.decode('ascii').split()[0]
        assert len(digest) == 64
        fetch(asset['browser_download_url'], asset['name'], 100_000_000, digest)
        save(out / 'COMPLETED.json', dict(status='retained-official-MACS2-sources-and-private-CPython-not-runtime-admission',
                                         MACS2=editions, private_Python=dict(path=asset['name'], **pin(out / asset['name'])),
                                         downloads=downloads, analysis_admitted=False,
                                         source_pins={Path(__file__).relative_to(ROOT).as_posix(): pin(Path(__file__))}))
        print('Retained3 exact MACS2 source editions and checksum-checked private CPython3.11')
    except Exception as error:
        save(out / 'FAILURE.json', dict(status='FAIL2_MACS2_SOURCE_INTAKE', error=str(error),
                                       required_action='Preserve downloads, correct the named source/hash/network issue and retry into NEW paths. No analysis admitted.'))
        raise


if __name__ == '__main__':
    main()
