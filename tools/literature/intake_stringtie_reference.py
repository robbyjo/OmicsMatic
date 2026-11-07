# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Capture official original StringTie editions and source archives privately; no analysis credit."""
import argparse
import datetime as dt
import hashlib
import json
from pathlib import Path, PurePosixPath
import tarfile
import urllib.error
import urllib.request

ROOT = Path(__file__).resolve().parents[2]
VERSIONS = ('1.3.0', '1.3.6', '2.1.2', '2.1.7', '2.2.1', '3.0.0', '3.0.3')


def pin(path):
    with path.open('rb') as stream:
        return dict(bytes=path.stat().st_size, sha256=hashlib.file_digest(stream, 'sha256').hexdigest())


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, required=True)
    out = parser.parse_args().out.resolve()
    assert out.is_relative_to((ROOT / 'build/literature-review/stringtie-runtime').resolve()) and not out.exists()
    out.mkdir(parents=True)
    files, editions = {}, []

    def fetch(name, url, maximum, expected_size=None, expected_digest=None):
        target = out / name
        assert not target.exists()
        target.parent.mkdir(parents=True, exist_ok=True)
        request = urllib.request.Request(url, headers={'User-Agent': 'OmicsMatic-StringTie-source-audit'})
        with urllib.request.urlopen(request, timeout=45) as response:
            total = 0
            with target.open('xb') as stream:
                while block := response.read(1024 * 1024):
                    total += len(block)
                    if total > maximum:
                        raise ValueError('Official download exceeds complete size limit: ' + name)
                    stream.write(block)
            value = dict(url=url, final_url=response.geturl(), headers=dict(response.headers), **pin(target))
        if expected_size is not None:
            assert value['bytes'] == expected_size
        if expected_digest:
            assert expected_digest == 'sha256:' + value['sha256']
        value['publisher_digest_available'] = bool(expected_digest)
        files[name] = value
        (out / 'PROGRESS.json').write_text(json.dumps(files, indent=2) + '\n', encoding='utf-8', newline='\n')
        return target

    def extract(archive, target):
        assert not target.exists()
        target.mkdir()
        members, total = {}, 0
        with tarfile.open(archive, 'r:gz') as tar:
            for member in tar:
                name = member.name
                p = PurePosixPath(name)
                assert not p.is_absolute() and '..' not in p.parts and '\\' not in name and name not in members
                assert len(members) < 50000 and (member.isdir() or member.isfile())
                total += member.size
                assert total <= 300000000
                path = target.joinpath(*p.parts)
                if member.isdir():
                    path.mkdir(parents=True, exist_ok=True)
                    members[name] = dict(directory=True)
                else:
                    path.parent.mkdir(parents=True, exist_ok=True)
                    assert not path.exists()
                    with tar.extractfile(member) as source, path.open('xb') as stream:
                        while block := source.read(1024 * 1024):
                            stream.write(block)
                    assert path.stat().st_size == member.size
                    members[name] = dict(mode=member.mode, **pin(path))
        return members

    try:
        for version in VERSIONS:
            release = None
            if version not in ('1.3.0', '1.3.6'):
                release = json.loads(fetch(version + '/release.json', 'https://api.github.com/repos/gpertea/stringtie/releases/tags/v' + version, 1048576).read_bytes())
                assert release['tag_name'] == 'v' + version and not release['draft'] and not release['prerelease']
            artifacts = {}
            for kind, filename in [('binary', 'stringtie-' + version + '.Linux_x86_64.tar.gz'), ('source', 'stringtie-' + version + '.tar.gz')]:
                asset = next(a for a in release['assets'] if a['name'] == filename) if release else None
                url = asset['browser_download_url'] if asset else 'https://ccb.jhu.edu/software/stringtie/dl/' + filename
                archive = fetch(version + '/archives/' + filename, url, 30000000, asset['size'] if asset else None, asset.get('digest') if asset else None)
                members = extract(archive, out / version / kind)
                artifacts[kind] = dict(archive=archive.relative_to(out).as_posix(), files=members)
            binaries = list((out / version / 'binary').rglob('stringtie'))
            assert len(binaries) == 1 and binaries[0].read_bytes()[:4] == b'\x7fELF'
            licenses = [p for p in (out / version / 'source').rglob('*') if p.is_file() and p.name.lower() in ('license', 'license.txt', 'copying')]
            assert licenses
            editions.append(dict(version=version, artifacts=artifacts, executable=binaries[0].relative_to(out).as_posix(),
                                 executable_pin=pin(binaries[0]), licenses={p.relative_to(out).as_posix(): pin(p) for p in licenses},
                                 license_review_required=True, runtime_executed=False))
        fetch('manual.html', 'https://www.ccb.jhu.edu/software/stringtie/index.shtml?t=manual', 4194304)
        fetch('history.html', 'https://github.com/gpertea/stringtie/wiki/History', 4194304)
        result = dict(status='captured-seven-official-StringTie-editions-not-project-integration', captured_utc=dt.datetime.now(dt.timezone.utc).isoformat(),
                      files=files, editions=editions, installation_scope='Private workspace extraction only; no system paths changed.',
                      runtime_executed=False, implementation_credit=False, released=False,
                      provenance_limit='SHA-256 records integrity. Editions lacking a publisher digest are explicitly identified; a recorded download hash is not a publisher signature.')
        (out / 'COMPLETED.json').write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8', newline='\n')
        print(json.dumps(dict(status=result['status'], editions=len(editions), downloads=len(files))))
        return 0
    except Exception as error:
        result = dict(status='failed-StringTie-reference-intake-dependent-analysis-skipped', error_type=type(error).__name__, error=str(error),
                      files=files, editions=editions, implementation_credit=False,
                      action='Read FAILURE.json and retained archives/PROGRESS.json. Correct access, network, integrity, archive-safety or capacity cause in a permitted environment; preserve this attempt and retry into a NEW --out directory. Do not run dependent StringTie analysis until source/license, exact binary and runtime health checks pass.')
        (out / 'FAILURE.json').write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8', newline='\n')
        (out / 'WARNINGS.md').write_text('# STRINGTIE REFERENCE INTAKE FAILED — DEPENDENT ANALYSIS SKIPPED\n\n' + str(error) + '\n\nRequired action: ' + result['action'] + '\n', encoding='utf-8', newline='\n')
        print(json.dumps(result))
        return 2


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable complete official archive checks')
    raise SystemExit(main())
