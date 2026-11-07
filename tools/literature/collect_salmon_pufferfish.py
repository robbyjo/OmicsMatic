# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Capture indexing dependency sources against checksums declared by exact original Salmon releases."""
import argparse
import concurrent.futures
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import re
import tarfile
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[2]
BASE = ROOT / 'build/literature-review/salmon-runtime'


def pin(path):
    with path.open('rb') as stream:
        return dict(bytes=path.stat().st_size, sha256=hashlib.file_digest(stream, 'sha256').hexdigest())


def write(path, value):
    path.write_text(json.dumps(value, indent=2) + '\n', encoding='utf-8', newline='\n')


def main():
    if not __debug__:
        raise RuntimeError('Never disable original dependency provenance checks')
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args()
    out = args.out.resolve()
    assert out.is_relative_to(BASE.resolve()) and not out.exists()
    out.mkdir()
    lock = json.loads((ROOT / 'tools/literature/salmon-runtime-lock.json').read_bytes())

    def capture(version):
        folder = out / version
        folder.mkdir()
        source = BASE / 'upstream-capture-1' / version / 'source.tar.gz'
        expected_source = lock['profiles']['linux-x86_64-salmon-' + version]['source_archive']
        assert pin(source) == {k: expected_source[k] for k in ['bytes', 'sha256']}
        with tarfile.open(source) as archive:
            raw = archive.extractfile('salmon-' + version + '/scripts/fetchPufferfish.sh').read()
        (folder / 'original-fetchPufferfish.sh').write_bytes(raw)  # Evidence only; never execute the original fetch script.
        text = raw.decode('utf-8')
        tag = re.search(r'^SVER=([^\s#]+)', text, re.M).group(1)
        expected = re.search(r'^EXPECTED_SHA256=([a-f0-9]{64})', text, re.M).group(1)
        assert re.fullmatch(r'salmon-v[0-9.]+', tag)
        url = 'https://github.com/COMBINE-lab/pufferfish/archive/' + tag + '.zip'
        target = folder / 'pufferfish.zip'
        request = urllib.request.Request(url, headers={'User-Agent': 'OmicsMatic-Salmon-source-provenance/1.0'})
        count = 0
        with urllib.request.urlopen(request, timeout=120) as response, target.open('xb') as stream:
            assert response.url.startswith('https://')
            while data := response.read(1048576):
                count += len(data)
                assert count <= 134217728
                stream.write(data)
        actual = pin(target)
        with zipfile.ZipFile(target) as archive:
            assert len(archive.infolist()) == len(set(archive.namelist())) <= 20000
            assert sum(p.file_size for p in archive.infolist()) <= 268435456
            assert archive.testzip() is None
        row = dict(version=version, tag=tag, url=url, expected_maintainer_sha256=expected, observed=actual,
                   maintainer_archive_checksum_matches=actual['sha256'] == expected, declaration_source_archive=expected_source,
                   declaration_script=pin(folder / 'original-fetchPufferfish.sh'), recorded_utc=datetime.now(timezone.utc).isoformat(),
                   scientific_runtime_or_user_analysis_admitted=False)
        write(folder / 'CAPTURED.json', row)
        print(version + ':maintainer Pufferfish archive checksum matches=' + str(row['maintainer_archive_checksum_matches']), flush=True)
        return row

    with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:
        rows = list(pool.map(capture, ['1.0.0', '1.1.0', '1.5.2', '1.8.0', '1.9.0']))
    write(out / 'COMPLETED.json', dict(status='captured-five-Salmon-indexing-source-archives-with-explicit-maintainer-checksum-status',
          releases=rows, producer=pin(Path(__file__)), all_maintainer_checksums_match=all(r['maintainer_archive_checksum_matches'] for r in rows),
          required_action='Use only exact matching declaration/archive bytes as original indexing-source evidence. Retain any mismatch explicitly; do not replace the maintainer checksum or claim historical binary/source equivalence from a current tag alone.',
          scientific_runtime_or_user_analysis_admitted=False))


if __name__ == '__main__':
    main()
