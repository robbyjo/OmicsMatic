# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Capture official fitdistrplus1.2-6 source; downloads only, no installation or analysis."""
import argparse
import gzip
import hashlib
import io
import json
from pathlib import Path, PurePosixPath
import tarfile
import urllib.request
from setup_deseq2_reference import ROOT, pin, save


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args()
    out = args.out.resolve()
    assert out.is_relative_to(ROOT / 'build/literature-review/fitdistrplus-runtime') and not out.exists()
    out.mkdir(parents=True)
    source_pin = pin(Path(__file__))
    def fetch(url, name, capacity):
        with urllib.request.urlopen(urllib.request.Request(url, headers={'User-Agent': 'OmicsMatic-source-audit/1'}), timeout=60) as response:
            raw = response.read(capacity + 1)
        assert len(raw) <= capacity
        (out / name).write_bytes(raw)
        return raw
    try:
        packages_url = 'https://cran.r-project.org/src/contrib/PACKAGES.gz'
        packages = fetch(packages_url, 'PACKAGES.gz', 16 << 20)
        records = gzip.decompress(packages).decode('utf8').split('\n\n')
        record = next(row for row in records if row.startswith('Package: fitdistrplus\n'))
        fields = dict(line.split(': ', 1) for line in record.splitlines() if ': ' in line and not line.startswith(' '))
        assert fields['Version'] == '1.2-6', 'Latest CRAN version changed; retain this intake and review exact version explicitly'
        url = 'https://cran.r-project.org/src/contrib/fitdistrplus_1.2-6.tar.gz'
        raw = fetch(url, 'fitdistrplus_1.2-6.tar.gz', 32 << 20)
        assert hashlib.md5(raw).hexdigest() == fields['MD5sum']
        files = {}
        with tarfile.open(fileobj=io.BytesIO(raw), mode='r:gz') as archive:
            for entry in archive.getmembers():
                name = PurePosixPath(entry.name)
                assert not name.is_absolute() and '..' not in name.parts and name.parts[0] == 'fitdistrplus'
                assert entry.isdir() or entry.isfile(), 'Refuse source links/special files'
                if entry.isfile():
                    relative = PurePosixPath(*name.parts[1:]).as_posix()
                    assert relative not in files and entry.size <= 32 << 20
                    value = archive.extractfile(entry).read()
                    target = out / 'source' / relative
                    target.parent.mkdir(parents=True, exist_ok=True)
                    target.write_bytes(value)
                    files[relative] = pin(target)
        description = (out / 'source/DESCRIPTION').read_text(encoding='utf8')
        assert 'Version: 1.2-6\n' in description and 'License: GPL (>= 2)\n' in description
        save(out / 'COMPLETED.json', dict(status='captured-official-fitdistrplus-source-not-installation', version='1.2-6',
            package_source_url=url, package_index_url=packages_url, archive=pin(out / 'fitdistrplus_1.2-6.tar.gz'),
            index=pin(out / 'PACKAGES.gz'), repository_MD5=fields['MD5sum'], source_members=files,
            source_pin=source_pin, paper_version_known=False, analysis_admitted=False, installation_performed=False))
        print('Captured official fitdistrplus1.2-6:', len(files), 'source files; no installation')
    except Exception as error:
        save(out / 'SKIP3.json', dict(status='SKIP3_ORIGINAL_SOURCE_UNAVAILABLE', detail=str(error), analysis_admitted=False,
            required_action='Preserve this intake. Where network access is permitted, obtain the exact official source and repository checksum, then retry NEW paths. Do not substitute an edition or infer the paper version. Dependent source-bound fitting analysis remains skipped.'))
        raise


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable source checks')
    main()
