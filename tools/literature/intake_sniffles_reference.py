# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Capture the five exact Sniffles source editions reported by the fixed cohort.

This downloads and inspects source only. It does not install or execute Sniffles,
change the catalog, or identify the binaries used by the authors.
"""
import argparse
import concurrent.futures
import io
import tarfile
import traceback
import urllib.request
from pathlib import Path, PurePosixPath

from setup_deseq2_reference import ROOT, pin, save

EDITIONS = {
    'v1.0.7': '752bd21266125c16f1ea17d1b19c7a76860d4856',
    'v2.0.2': '5737eb7bc5218c79f990d9ddf138c144bdd6cbad',
    'v2.0.7': '60e70150ca943041250867d0facf48463da1aee8',
    'v2.3.2': '034d8a907ba535d53169975e39174418d44c6f26',
    'v2.4': '7f71d13d011b0df3d29da90a294507530181e2b9',
}
MAX_ARCHIVE = 128 * 1024 * 1024
MAX_TOTAL = 512 * 1024 * 1024


def capture(out, item):
    tag, commit = item
    home = out / tag
    home.mkdir()
    url = 'https://codeload.github.com/fritzsedlazeck/Sniffles/tar.gz/' + commit
    request = urllib.request.Request(url, headers={'User-Agent': 'OmicsMatic-primary-source-audit'})
    with urllib.request.urlopen(request, timeout=180) as response:
        raw = response.read(MAX_ARCHIVE + 1)
        assert len(raw) <= MAX_ARCHIVE
        final = response.url
    archive = home / (commit + '.tar.gz')
    archive.write_bytes(raw)
    save(home / 'download.json', dict(requested_url=url, final_url=final, **pin(archive)))
    root = home / 'source'
    root.mkdir()
    members, total, names = {}, 0, set()
    prefix = 'Sniffles-' + commit
    with tarfile.open(fileobj=io.BytesIO(raw), mode='r:gz') as opened:
        for member in opened.getmembers():
            path = PurePosixPath(member.name)
            assert not path.is_absolute() and '..' not in path.parts
            assert '\\' not in member.name and ':' not in member.name
            assert path.parts[0] == prefix and member.name not in names
            names.add(member.name)
            if member.isdir():
                continue
            assert member.isfile(), 'Refusing archive links/devices: ' + member.name
            assert len(path.parts) > 1 and member.size <= MAX_ARCHIVE
            total += member.size
            assert total <= MAX_TOTAL and len(members) < 20000
            relative = PurePosixPath(*path.parts[1:]).as_posix()
            assert relative not in members
            file = root / relative
            assert file.resolve().is_relative_to(root.resolve())
            file.parent.mkdir(parents=True, exist_ok=True)
            content = opened.extractfile(member).read(MAX_ARCHIVE + 1)
            assert len(content) == member.size
            file.write_bytes(content)
            members[relative] = pin(file)
    assert 'LICENSE' in members and 'README.md' in members
    # Inspect every edition; the C++ and Python releases both declare MIT.
    license_text = (root / 'LICENSE').read_text(encoding='utf8')
    if tag == 'v1.0.7':
        assert 'The MIT License (MIT)' in license_text
        assert any(n.endswith('.cpp') for n in members)
    else:
        assert 'MIT License' in license_text
        assert 'setup.cfg' in members and 'pyproject.toml' in members
    record = dict(tag=tag, commit=commit, requested_url=url,
                  archive=dict(path=archive.relative_to(ROOT).as_posix(), **pin(archive)),
                  members=members, total_member_bytes=total,
                  license_path='LICENSE', source_patched=False,
                  source_only=True, executed=False, paper_binary_identified=False)
    save(home / 'SOURCE.json', record)
    return tag, record


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args()
    out = args.out.resolve()
    assert not out.exists() and out.is_relative_to(ROOT / 'build/literature-review/sniffles-runtime')
    out.mkdir(parents=True)
    state = dict(status='FAIL2_SNIFFLES_SOURCE_INTAKE', analysis_admitted=False,
                 implementation_credit=False, catalog_changed=False, whole_goal_complete=False)
    try:
        with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:
            records = dict(pool.map(lambda item: capture(out, item), EDITIONS.items()))
        assert set(records) == set(EDITIONS)
        state.update(status='passed-five-exact-Sniffles-source-editions-only', editions=records,
                     source_pins={Path(__file__).resolve().relative_to(ROOT).as_posix(): pin(Path(__file__).resolve())})
    except Exception as error:
        state['error'] = repr(error)
        (out / 'error.traceback').write_text(traceback.format_exc(), encoding='utf8')
    save(out / 'COMPLETED.json', state)
    print(state['status'], state.get('error', ''))
    return 0 if state['status'].startswith('passed-') else 2


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable complete source archive checks')
    raise SystemExit(main())
