# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Capture complete original gene/Ballgown table families, including wildcard feature boundaries."""
import hashlib
import json
from pathlib import Path
import stat
import zipfile

ROOT = Path(__file__).resolve().parents[2]
BASE = ROOT / 'build/literature-review/stringtie-runtime'


def pin(path):
    raw = path.read_bytes()
    return dict(bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest())


def main():
    target = ROOT / 'src/test/resources/org/jlinalg/sequence/stringtie-tables'
    assert not target.exists()
    entries, cases = {}, []

    def add(path, name):
        assert path.is_file() and not path.is_symlink() and name not in entries
        entries[name] = path.read_bytes()

    def capture(folder, name, guide, gene, receipt, expected_files):
        assert receipt['exit_code'] == 0 and not receipt['timed_out']
        assert all(pin(folder / relative) == value for relative, value in expected_files.items())
        files = ['transcripts.gtf', gene, 't_data.ctab', 'e_data.ctab', 'i_data.ctab', 'e2t.ctab', 'i2t.ctab', 'fit.stdout', 'fit.stderr', 'fit.process.json']
        for relative in files:
            add(folder / relative, name + '/' + relative)
        add(guide, name + '/guide.gtf')
        cases.append(dict(path=name, gene_file=gene, source_guide=guide.relative_to(ROOT).as_posix(),
                          process=receipt, original_files=expected_files))

    old = BASE / 'original-controls-1'
    for edition in json.loads((old / 'CAPTURED.json').read_bytes())['editions']:
        for case in edition['cases']:
            if case['case'] in ('estimate', 'empty-estimate'):
                capture(old / edition['version'] / case['case'], 'baseline/' + edition['version'] + '/' + case['case'],
                        old / 'inputs/guide.gtf', 'gene-abundance.tsv', case['process'], case['files'])
    boundary = BASE / 'table-boundaries-1'
    for case in json.loads((boundary / 'CAPTURED.json').read_bytes())['cases']:
        capture(boundary / case['version'] / case['case'], 'boundaries/' + case['version'] + '/' + case['case'],
                boundary / 'inputs' / (case['case'] + '.gtf'), 'genes.tsv', case['process'], case['files'])
    wildcard = BASE / 'table-wildcard-1'
    for case in json.loads((wildcard / 'CAPTURED.json').read_bytes())['cases']:
        capture(wildcard / case['version'], 'wildcard/' + case['version'], wildcard / 'inputs/guide.gtf', 'genes.tsv', case['process'], case['files'])
    assert len(cases) == 35
    intake = BASE / 'source-intake-1'
    original = json.loads((intake / 'COMPLETED.json').read_bytes())
    source_proof = {}
    for edition in original['editions']:
        version = edition['version']
        for name in ['LICENSE', 'tablemaker.cpp', 'tablemaker.h', 'rlink.h', 'stringtie.cpp']:
            path = intake / version / 'source' / ('stringtie-' + version) / name
            relative = path.relative_to(intake / version / 'source').as_posix()
            assert pin(path) == {k: edition['artifacts']['source']['files'][relative][k] for k in ['bytes', 'sha256']}
            add(path, 'original-source/' + version + '/' + name)
            source_proof[version + '/' + name] = pin(path)
    sources = {name: pin(ROOT / name) for name in ['tools/literature/probe_stringtie_table_boundaries.py',
                                                 'tools/literature/probe_stringtie_wildcard_retained.py',
                                                 'tools/literature/freeze_stringtie_tables_fixtures.py']}
    report = dict(schema_version=1, status='captured-35-original-StringTie-gene-and-Ballgown-table-families',
                  cases=cases, original_source=source_proof, helper_sources=sources,
                  reused_original_families=14, fresh_original_families=21, implementation_credit=False,
                  warning='This is original source/output evidence, not project integration validation. Unknown-strand compatibility changes shared feature populations; retain exact source feature strands and all original numeric values. Table structure does not certify inference, alignment counts, coverage moments or normalization.')
    entries['CASES.json'] = (json.dumps(report, indent=2) + '\n').encode()
    target.mkdir()
    archive = target / 'original-table-controls.zip'
    members = {}
    with zipfile.ZipFile(archive, 'x', compression=zipfile.ZIP_DEFLATED, compresslevel=9) as z:
        for name, raw in sorted(entries.items()):
            info = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0)); info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = (stat.S_IFREG | 0o644) << 16; z.writestr(info, raw)
            members[name] = dict(bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest())
    with zipfile.ZipFile(archive) as z:
        assert z.testzip() is None and len(z.namelist()) == len(set(z.namelist())) == len(members)
        for name, value in members.items():
            raw = z.read(name); assert dict(bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest()) == value
    (target / 'CAPTURE.json').write_text(json.dumps(dict(**report, archive=pin(archive), archive_files=members), indent=2) + '\n', encoding='utf-8', newline='\n')
    (target / 'SHA256SUMS').write_text(''.join(pin(target / name)['sha256'] + '  ' + name + '\n' for name in ['CAPTURE.json', archive.name]), encoding='ascii', newline='\n')
    (target / '.gitattributes').write_text('original-table-controls.zip -text\nCAPTURE.json -text\nSHA256SUMS -text\n', encoding='ascii', newline='\n')
    print(json.dumps(dict(status=report['status'], archive=pin(archive), members=len(members))))


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable original table/source fixture checks')
    main()
