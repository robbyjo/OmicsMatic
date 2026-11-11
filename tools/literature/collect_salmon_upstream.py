# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Capture official historical Salmon releases; download only, no installation."""
import argparse
import concurrent.futures
import hashlib
import json
from pathlib import Path
import sys
import urllib.request

ROOT = Path(__file__).resolve().parents[2]
VERSIONS = ('1.0.0', '1.1.0', '1.5.2', '1.8.0', '1.9.0')


def pin(path):
    with path.open('rb') as stream:
        return dict(bytes=path.stat().st_size, sha256=hashlib.file_digest(stream, 'sha256').hexdigest())
def write(path, value): path.write_text(json.dumps(value, indent=2)+'\n', encoding='utf-8', newline='\n')


def fetch(url, path, maximum):
    assert not path.exists() and url.startswith(('https://api.github.com/repos/COMBINE-lab/salmon/',
        'https://codeload.github.com/COMBINE-lab/salmon/', 'https://github.com/COMBINE-lab/salmon/releases/download/'))
    request = urllib.request.Request(url, headers={'User-Agent':'OmicsMatic-literature-toolbox/1.0', 'Accept':'application/vnd.github+json' if 'api.github.com' in url else '*/*'})
    size = 0
    with urllib.request.urlopen(request, timeout=180) as response, path.open('xb') as target:
        final = response.url
        assert final.startswith('https://')
        while raw := response.read(1024*1024):
            size += len(raw)
            if size > maximum: raise RuntimeError('Upstream capture capacity exceeded: '+url)
            target.write(raw)
    return dict(url=url, final_url=final, **pin(path))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args(); out = args.out.resolve()
    assert out.is_relative_to((ROOT / 'build/literature-review/salmon-runtime').resolve()) and not out.exists()
    out.mkdir(parents=True)
    def one(version):
        folder = out / version; folder.mkdir(); records = []
        records.append(fetch('https://api.github.com/repos/COMBINE-lab/salmon/releases/tags/v'+version, folder/'release.json', 4*1024*1024))
        release = json.loads((folder/'release.json').read_bytes()); assert release['tag_name'] == 'v'+version
        assets = [a for a in release['assets'] if a['name'].endswith('.tar.gz') and 'linux' in a['name'].lower()]
        assert len(assets) == 1, (version, [(a['name'],a['size']) for a in release['assets']])
        asset = assets[0]; path = folder / asset['name']
        records.append(fetch(asset['browser_download_url'], path, 512*1024*1024)); assert path.stat().st_size == asset['size']
        if asset.get('digest'): assert asset['digest'] == 'sha256:'+pin(path)['sha256']
        records.append(fetch('https://codeload.github.com/COMBINE-lab/salmon/tar.gz/refs/tags/v'+version, folder/'source.tar.gz', 256*1024*1024))
        result = dict(version=version, status='captured-official-release-and-source-no-install', files=records,
            official_asset_digest=asset.get('digest'), integrity_not_authentication=True)
        write(folder/'CAPTURED.json', result); print(json.dumps(dict(version=version, captured=True, assets=[p.name for p in folder.iterdir()])), flush=True)
        return result
    with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool: results = list(pool.map(one, VERSIONS))
    write(out/'COMPLETED.json', dict(status='captured-five-exact-Salmon-paper-editions-not-runtime-admitted', versions=list(VERSIONS), releases=results,
        collector=pin(Path(__file__)), installed=False, user_analysis_admitted=False))


if __name__ == '__main__':
    try: main()
    except Exception as error:
        print('SALMON CAPTURE FAIL2: '+str(error)+'\nRequired action: retain partial downloads, inspect the named network/metadata/capacity error and use NEW paths for a corrected capture. No runtime or analysis is admitted.', file=sys.stderr)
        raise
