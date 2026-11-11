# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Retain exact original MTAG sources and primary-paper intake; no analysis or catalog credit."""
import argparse
import gzip
import hashlib
import io
import json
import re
import urllib.request
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path
from collect_omics_review import paragraphs
from setup_deseq2_reference import ROOT, pin, save


def get(url):
    request = urllib.request.Request(url, headers={'User-Agent': 'OmicsMatic-primary-source-review/1.0'})
    with urllib.request.urlopen(request, timeout=60) as response:
        return response.read()


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--out', type=Path, required=True)
    a = p.parse_args()
    out = a.out.resolve()
    assert out.is_relative_to(ROOT / 'build/literature-review/mtag-runtime') and not out.exists()
    out.mkdir(parents=True)
    try:
        api = get('https://api.github.com/repos/JonJala/mtag/commits/master')
        (out / 'upstream-commit.json').write_bytes(api)
        commit = json.loads(api)['sha']
        url = 'https://codeload.github.com/JonJala/mtag/zip/' + commit
        raw = get(url)
        archive = out / 'upstream.zip'
        archive.write_bytes(raw)
        inventory = {}
        with zipfile.ZipFile(io.BytesIO(raw)) as z:
            prefix = 'mtag-' + commit + '/'
            for name in z.namelist():
                assert name.startswith(prefix)
                suffix = name[len(prefix):]
                if not suffix or name.endswith('/'):
                    continue
                path = out / 'source' / suffix
                assert path.resolve().is_relative_to(out / 'source')
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_bytes(z.read(name))
                inventory[suffix] = pin(path)
        uses = []
        for paper in json.loads((ROOT / 'docs/literature/omics-2023-2026/papers.json').read_bytes()):
            tool = next((t for t in paper['tools'] if t['tool_id'] == 'mtag'), None)
            if not tool:
                continue
            xml = gzip.decompress((ROOT / 'build/literature-review/xml' / (paper['pmcid'] + '.xml.gz')).read_bytes())
            assert hashlib.sha256(xml).hexdigest() == paper['xml_sha256']
            matches = [q for q in paragraphs(ET.fromstring(xml)) if re.search(r'\bMTAG\b', q['match_text'], re.I)]
            anchor = next(q for q in matches if q['paragraph_index'] == tool['evidence_paragraph_index'])
            assert hashlib.sha256(anchor['text'].encode()).hexdigest() == tool['evidence_paragraph_sha256']
            uses.append(dict(paper_id=paper['paper_id'], pmcid=paper['pmcid'], doi=paper['doi'], xml_sha256=paper['xml_sha256'], catalog_observation=tool, primary_paragraphs=matches))
        assert len(uses) == 5
        save(out / 'private-primary-intake.json', dict(uses=uses, full_text_not_for_redistribution=True))
        save(out / 'COMPLETED.json', dict(status='PASS_PRIMARY_INTAKE_ONLY_NO_RUNTIME_OR_CATALOG_CREDIT', commit=commit, url=url, archive=pin(archive), files=inventory, primary_intake=pin(out / 'private-primary-intake.json'), papers=5, analysis_run=False))
        print(json.dumps(dict(commit=commit, files=len(inventory), archive_bytes=len(raw), papers=5)))
    except Exception as error:
        save(out / 'FAILURE.json', dict(error=str(error), analysis_run=False))
        raise


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Do not disable source integrity assertions')
    main()
