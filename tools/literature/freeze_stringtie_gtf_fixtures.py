# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Freeze all original StringTie controls for native GTF contract tests; do not credit a public integration."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import stat
import zipfile

ROOT = Path(__file__).resolve().parents[2]


def pin(path):
    raw = path.read_bytes()
    return dict(bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest())


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--controls', type=Path, required=True)
    parser.add_argument('--intake', type=Path, required=True)
    args = parser.parse_args()
    controls, intake = args.controls.resolve(), args.intake.resolve()
    assert controls.is_relative_to((ROOT / 'build/literature-review/stringtie-runtime').resolve())
    assert intake.is_relative_to((ROOT / 'build/literature-review/stringtie-runtime').resolve())
    target = ROOT / 'src/test/resources/org/jlinalg/sequence/stringtie-gtf'
    assert not target.exists()
    capture = json.loads((controls / 'CAPTURED.json').read_bytes())
    original = json.loads((intake / 'COMPLETED.json').read_bytes())
    assert capture['original_intake'] == pin(intake / 'COMPLETED.json')
    entries, cases, failed = {}, [], []
    for edition in capture['editions']:
        version = edition['version']
        assert edition['status'] == 'captured-original-protocols-not-project-adapter-validation' and len(edition['cases']) == 7
        for case in edition['cases']:
            name, receipt = case['case'], case['process']
            assert not receipt['timed_out'] and receipt['exit_code'] in (0, 1)
            for relative, value in case['files'].items():
                path = controls / version / name / relative
                assert pin(path) == value
            if receipt['exit_code'] != 0:
                assert name == 'paper171-literal-rf' or (version, name) == ('1.3.0', 'paper171-documented-rf')
                failed.append(dict(version=version, case=name, process=receipt))
                assert not (controls / version / name / 'transcripts.gtf').exists()
                continue
            raw = (controls / version / name / 'transcripts.gtf').read_text()
            rows = [line.split('\t') for line in raw.splitlines() if line and not line.startswith('#')]
            tx = [r for r in rows if r[2] == 'transcript']
            assert all(len(r) == 9 and r[2] in ('transcript', 'exon') for r in rows)
            assert len(tx) == (3 if name in ('assembly', 'paper171-documented-rf') else 5 if name == 'merge' else 4)
            profile = 'MERGE' if name == 'merge' else 'ESTIMATION' if name in ('estimate', 'empty-estimate') else 'ASSEMBLY'
            cases.append(dict(path=version + '/' + name, profile=profile, features=len(rows), transcripts=len(tx),
                              exons=len(rows) - len(tx), transcript_ids=[re.search(r'transcript_id "([^"]+)";', r[8]).group(1) for r in tx]))
    assert len(cases) == 41 and len(failed) == 8
    for path in sorted(controls.rglob('*')):
        assert not path.is_symlink()
        if path.is_file():
            assert path.stat().st_size < 1048576
            entries[path.relative_to(controls).as_posix()] = path.read_bytes()
    for edition in original['editions']:
        version = edition['version']
        name = version + '/source/stringtie-' + version + '/LICENSE'
        assert pin(intake / name) == edition['licenses'][name]
        entries['licenses/' + version + '.txt'] = (intake / name).read_bytes()
    entries['official-intake.json'] = (intake / 'COMPLETED.json').read_bytes()
    proof = dict(schema_version=1, status='captured-original-StringTie-GTF-test-fixtures-not-public-integration',
                 successful_protocols=cases, retained_failed_protocols=failed,
                 fixture=pin(controls / 'FIXTURE.json'), original_capture=pin(controls / 'CAPTURED.json'),
                 collector=pin(ROOT / 'tools/literature/collect_stringtie_controls.py'),
                 freezer=pin(Path(__file__)), implementation_credit=False, project_adapter_validated=False,
                 warning='All seven original editions reject the literal paper171 -rf option. The distinct documented --rf control succeeds in six editions and is unavailable in1.3.0. No silent replacement or historical-output equivalence is claimed. All original files and failures are preserved.')
    entries['GTF-CASES.json'] = (json.dumps(proof, indent=2) + '\n').encode()
    target.mkdir()
    archive = target / 'original-gtf-controls.zip'
    members = {}
    with zipfile.ZipFile(archive, 'x', compression=zipfile.ZIP_DEFLATED, compresslevel=9) as z:
        for name, raw in sorted(entries.items()):
            info = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = (stat.S_IFREG | 0o644) << 16
            z.writestr(info, raw)
            members[name] = dict(bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest())
    with zipfile.ZipFile(archive) as z:
        assert z.testzip() is None and len(z.namelist()) == len(set(z.namelist())) == len(members)
        for name, value in members.items():
            raw = z.read(name)
            assert dict(bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest()) == value
    (target / 'CAPTURE.json').write_text(json.dumps(dict(**proof, archive=pin(archive), archive_files=members), indent=2) + '\n', encoding='utf-8', newline='\n')
    (target / 'SHA256SUMS').write_text(''.join(pin(target / name)['sha256'] + '  ' + name + '\n' for name in ['CAPTURE.json', archive.name]), encoding='ascii', newline='\n')
    (target / '.gitattributes').write_text('original-gtf-controls.zip -text\nCAPTURE.json -text\nSHA256SUMS -text\n', encoding='ascii', newline='\n')
    print(json.dumps(dict(status=proof['status'], successes=len(cases), failures=len(failed), archive=pin(archive), members=len(members))))


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable complete original fixture checks')
    main()
