# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Save immutable historical scDRS sources without granting implementation credit."""
import argparse
import hashlib
import io
import json
import urllib.request
import zipfile
from pathlib import Path

from setup_deseq2_reference import ROOT, pin, save

VERSIONS = {
    '1.0.0': '79986ff5415537248e84eb8a4a34befacedf699c',
    '1.0.1': '075527009a20f4f13287e2625a2828dc88785481',
    '1.0.2': '7f8f8d5754e1e3057916cd3fbe720b96f90c1c1b',
}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args()
    out = args.out.resolve()
    assert out.is_relative_to(ROOT / 'build/literature-review/scdrs-runtime')
    assert not out.exists(), 'Preserve each previous intake attempt'
    out.mkdir(parents=True)
    try:
        profiles = {}
        for version, commit in VERSIONS.items():
            url = 'https://codeload.github.com/martinjzhang/scDRS/zip/' + commit
            request = urllib.request.Request(url, headers={'User-Agent': 'OmicsMatic-source-review/1.0'})
            with urllib.request.urlopen(request, timeout=90) as response:
                raw = response.read(100_000_001)
            assert len(raw) <= 100_000_000, 'Source archive exceeds the declared intake bound'
            base = out / version
            base.mkdir()
            archive = base / 'upstream.zip'
            archive.write_bytes(raw)
            files = {}
            with zipfile.ZipFile(io.BytesIO(raw)) as z:
                prefix = 'scDRS-' + commit + '/'
                for info in z.infolist():
                    assert info.filename.startswith(prefix)
                    suffix = info.filename[len(prefix):]
                    if not suffix or info.is_dir():
                        continue
                    target = base / 'source' / suffix
                    assert target.resolve().is_relative_to(base / 'source')
                    assert (info.external_attr >> 16) & 0o170000 != 0o120000, 'No symlink extraction'
                    target.parent.mkdir(parents=True, exist_ok=True)
                    target.write_bytes(z.read(info))
                    files[suffix] = pin(target)
            license_path = base / 'source/LICENSE'
            assert 'MIT License' in license_path.read_text(encoding='utf-8')
            profiles[version] = dict(commit=commit, url=url, archive=pin(archive),
                                     license=pin(license_path), files=files)
        save(out / 'COMPLETED.json', dict(status='PASS_THREE_HISTORICAL_SOURCE_INTAKES_ONLY',
             profiles=profiles, catalog_modified=False, runtime_installed=False,
             new_catalog_credit=False))
        print(json.dumps({v: dict(commit=p['commit'], files=len(p['files']),
                                 archive_bytes=p['archive']['bytes']) for v, p in profiles.items()}))
    except Exception as error:
        save(out / 'FAILURE.json', dict(error=str(error), new_catalog_credit=False))
        raise


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Do not disable source integrity assertions')
    main()
