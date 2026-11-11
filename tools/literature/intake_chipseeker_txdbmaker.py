# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Capture the official TxDb fixture-builder source without installing or executing it."""
import argparse
import json
import tarfile
import urllib.request
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, required=True)
    out = parser.parse_args().out.resolve()
    assert out.is_relative_to(ROOT / 'build/literature-review/chipseeker-runtime') and not out.exists()
    out.mkdir()
    url = 'https://bioconductor.org/packages/3.23/bioc/src/contrib/txdbmaker_1.8.0.tar.gz'
    archive = out / 'txdbmaker_1.8.0.tar.gz'
    with urllib.request.urlopen(urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'}), timeout=120) as response:
        raw = response.read(32 * 1024 * 1024 + 1)
        assert len(raw) <= 32 * 1024 * 1024
        headers = dict(response.headers)
    archive.write_bytes(raw)
    files = {}
    with tarfile.open(archive) as tar:
        for member in tar.getmembers():
            if member.isfile() and (member.name in {'txdbmaker/DESCRIPTION', 'txdbmaker/NAMESPACE'} or
                                     member.name.startswith(('txdbmaker/R/', 'txdbmaker/man/'))):
                target = out / 'selected-source' / member.name
                assert target.is_relative_to(out)
                target.parent.mkdir(exist_ok=True, parents=True)
                target.write_bytes(tar.extractfile(member).read())
                files[member.name] = pin(target)
    save(out / 'SOURCE.json', dict(url=url, response_headers=headers, archive=pin(archive),
                                  selected_files=files, installation_performed=False,
                                  source=pin(Path(__file__))))
    print(json.dumps(dict(status='captured-original-txdbmaker-source-no-install', files=len(files))))


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable source bindings')
    main()
