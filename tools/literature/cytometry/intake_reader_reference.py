#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Retrieve pinned original reader/control files into a NEW directory; no installation or analysis."""
import argparse
import hashlib
import json
import tarfile
import traceback
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path, PurePosixPath

ACTION = ('Reference intake only. Native Java stored-channel extraction requires no external scientific runtime. '
          'Preserve originals and failed intakes. If network access or permitted installation is unavailable, '
          'skip the dependent reference comparison; do not substitute an edition, bypass TLS or truncate inputs. '
          'Use NEW paths after resolving the named cause. Source identities establish integrity, not authenticity.')


def save(path, value):
    path.write_text(json.dumps(value, indent=2)+'\n', encoding='utf8', newline='\n')


def main():
    p = argparse.ArgumentParser(description=__doc__); p.add_argument('--out', required=True, type=Path)
    out = p.parse_args().out.absolute(); out.mkdir(parents=True, exist_ok=False)
    completed = []
    try:
        profile = json.loads(Path(__file__).with_name('reader-reference-lock.json').read_bytes())
        for row in profile['files']:
            name = PurePosixPath(row['file'])
            if name.is_absolute() or '..' in name.parts or not name.parts:
                raise ValueError('Unexpected pinned control path')
            path = out.joinpath(*name.parts); path.parent.mkdir(parents=True, exist_ok=True)
            request = urllib.request.Request(row['url'], headers={'User-Agent': 'OmicsMatic-pinned-cytometry-reference-intake'})
            with urllib.request.urlopen(request, timeout=120) as response:
                raw = response.read(row['bytes']+1)
                final = urllib.parse.urlsplit(response.geturl())
                if final.scheme != 'https':
                    raise ValueError('Unexpected non-HTTPS reference redirect')
            path.write_bytes(raw)
            if len(raw) != row['bytes'] or hashlib.sha256(raw).hexdigest() != row['sha256']:
                raise ValueError('Pinned complete reference file differs: '+row['file'])
            completed.append(dict(row, final_url=urllib.parse.urlunsplit(final._replace(query='', fragment='')), redirect_query_omitted=bool(final.query or final.fragment)))
        source = out/'flowio-source'; source.mkdir()
        with tarfile.open(out/'flowio-1.4.0.tar.gz') as archive:
            seen = set()
            for member in archive:
                name = PurePosixPath(member.name)
                if name.is_absolute() or '..' in name.parts or name in seen or not name.parts or name.parts[0] != 'flowio-1.4.0' or not (member.isdir() or member.isfile()):
                    raise ValueError('Unexpected/duplicate original source archive member')
                seen.add(name); target = source.joinpath(*name.parts)
                if member.isdir():
                    target.mkdir(parents=True, exist_ok=True)
                else:
                    target.parent.mkdir(parents=True, exist_ok=True)
                    with archive.extractfile(member) as stream:
                        target.write_bytes(stream.read())
        state = dict(status='PASS_PINNED_READER_REFERENCE_INTAKE_ONLY', files=completed, source_version=profile['flowio_version'],
                     original_source_license='BSD-3-Clause; original LICENSE retained in flowio-source', analysis_executed=False,
                     software_installed=False, required_action=ACTION)
        code = 0
    except Exception as error:
        code = 3 if isinstance(error, (urllib.error.URLError, TimeoutError, PermissionError)) else 2
        state = dict(status='SKIP3_REFERENCE_UNAVAILABLE' if code == 3 else 'FAIL2_REFERENCE_INTAKE_NO_RESULT', error=repr(error),
                     traceback=traceback.format_exc(), completed_files=completed, required_action=ACTION)
    save(out/('COMPLETED.json' if code == 0 else 'FAILURE.json'), state)
    (out/'WARNINGS.md').write_text('# '+state['status']+'\n\n'+state.get('error', 'Reference files only; no scientific analysis or installation.')+'\n\n'+ACTION+'\n', encoding='utf8', newline='\n')
    print(state['status']); return code


if __name__ == '__main__':
    raise SystemExit(main())
