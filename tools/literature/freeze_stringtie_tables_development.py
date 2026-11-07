# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Freeze native table/reference topology evidence, retaining failures and no public-integration credit."""
import hashlib
import json
from pathlib import Path, PurePosixPath
import stat
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[2]
BASE = ROOT / 'build/literature-review/stringtie-runtime'


def read(path):
    return json.loads(path.read_bytes())


def pin(path):
    with path.open('rb') as stream:
        return dict(bytes=path.stat().st_size, sha256=hashlib.file_digest(stream, 'sha256').hexdigest())


def main():
    target = ROOT / 'docs/literature/stringtie-tables-development'
    assert not target.exists()
    matrix = BASE / 'native-table-matrix-1'
    proof = read(matrix / 'COMPLETED.json')
    assert proof['status'] == 'passed-native-StringTie-gene-and-Ballgown-complete-topology-audits'
    assert not (matrix / 'INCOMPLETE.json').exists() and not (matrix / 'FAILURE.json').exists()
    assert proof['exact_new_source_classes'] == len(proof['new_classes']) == 14 and proof['unchanged_parent_entries'] == 19546
    assert sorted(r['name'] for r in proof['platform_validation']) == ['linux-java21', 'windows-java21', 'windows-java25']
    assert all(r['tests'] == r['passed'] == 191 and r['skipped'] == 0 for r in proof['platform_validation'])
    assert proof['original_table_families'] == 35 and proof['fresh_original_table_families'] == 21 and proof['reused_original_table_families'] == 14
    assert proof['strict_Java17_source'] and proof['javadoc_passed']
    assert not any(proof[k] for k in ['public_workflow_available', 'implementation_credit', 'inference_or_normalization_certified', 'released', 'pushed', 'deployed', 'whole_goal_complete'])
    assert all(pin(ROOT / name) == value for name, value in proof['source_pins'].items())
    assert all(pin(ROOT / name) == value for name, value in proof['dependency_pins'].items())
    assert pin(ROOT / proof['artifact']['path']) == {k: proof['artifact'][k] for k in ['bytes', 'sha256']}
    assert pin(ROOT / 'build/cli/omicsmatic-0.3.7.jar') == proof['canonical_executable_unchanged']
    parent = ROOT / 'docs/literature/stringtie-gtf-development'
    assert pin(parent / 'VALIDATION.json') == proof['parent_validation'] and pin(parent / 'controls.zip') == proof['parent_archive']
    previous = read(parent / 'VALIDATION.json')
    assert all(pin(ROOT / name) == value for name, value in previous['source_pins'].items())
    with zipfile.ZipFile(ROOT / proof['parent_artifact']['path']) as old, zipfile.ZipFile(ROOT / proof['artifact']['path']) as new:
        assert new.testzip() is None and len(new.namelist()) == len(set(new.namelist()))
        assert set(new.namelist()) - set(old.namelist()) == set(proof['new_classes'])
        assert all(old.read(name) == new.read(name) for name in old.namelist())
        for name, value in proof['new_classes'].items():
            raw = new.read(name); assert dict(bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest()) == value

    intake = BASE / 'source-intake-1'
    official = read(intake / 'COMPLETED.json')
    assert len(official['editions']) == 7
    assert all(pin(intake / e['executable']) == e['executable_pin'] for e in official['editions'])
    boundary, wildcard = BASE / 'table-boundaries-1', BASE / 'table-wildcard-1'
    fresh = []
    for case in read(boundary / 'CAPTURED.json')['cases']:
        assert case['process']['exit_code'] == 0 and not case['process']['timed_out']
        assert all(pin(boundary / case['version'] / case['case'] / name) == value for name, value in case['files'].items())
        fresh.append(dict(version=case['version'], case=case['case']))
    for case in read(wildcard / 'CAPTURED.json')['cases']:
        assert case['process']['exit_code'] == 0 and not case['process']['timed_out']
        assert all(pin(wildcard / case['version'] / name) == value for name, value in case['files'].items())
        fresh.append(dict(version=case['version'], case='retained-unknown-representative'))
    assert len(fresh) == 21
    assert pin(wildcard / 'inputs/guide.gtf') == read(wildcard / 'CAPTURED.json')['guide']
    assert all(pin(boundary / 'inputs' / name) == value for name, value in read(boundary / 'CAPTURED.json')['inputs'].items())
    fixture = ROOT / 'src/test/resources/org/jlinalg/sequence/stringtie-tables'
    original_cases = read(fixture / 'CAPTURE.json')
    assert len(original_cases['cases']) == 35
    assert pin(fixture / 'original-table-controls.zip') == original_cases['archive']
    with zipfile.ZipFile(fixture / 'original-table-controls.zip') as z:
        assert z.testzip() is None and len(z.namelist()) == len(set(z.namelist())) == len(original_cases['archive_files']) == 421
        for name, value in original_cases['archive_files'].items():
            raw = z.read(name); assert dict(bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest()) == value
    for name, expected in [('StringTieGtfTest', 92), ('StringTieTablesTest', 99)]:
        test = ET.fromstring((BASE / 'table-java-development-2' / ('TEST-org.jlinalg.sequence.' + name + '.xml')).read_bytes())
        assert tuple(int(test.get(k)) for k in ['tests', 'failures', 'errors', 'skipped']) == (expected, 0, 0, 0)
    assert 'BUILD SUCCESSFUL' in (BASE / 'table-java-development-2/gradle.stdout').read_text()
    assert read(BASE / 'table-java-development-1/FAILURE.json')['failed'] == 1

    data = ROOT / 'docs/literature/omics-2023-2026'
    catalog = read(data / 'bigwig-catalog-validation.json')
    with zipfile.ZipFile(data / catalog['archive']['path']) as z:
        for name in ['papers.json', 'tool-inventory.json', 'summary.json', 'catalog-enrichment.json', 'WARNINGS.md']:
            assert (data / name).read_bytes() == z.read('catalog-snapshot/' + name)
    assert next(t for t in read(data / 'tool-inventory.json') if t['id'] == 'stringtie')['implementation_status'] == 'missing'
    assert pin(ROOT / 'tools/literature/reviewed-uses.json')['sha256'] == read(data / 'bigwig-role-coverage.json')['reviews_sha256']
    sums = dict(line.split('  ', 1)[::-1] for line in (data / 'SHA256SUMS').read_text().splitlines())
    assert len(sums) == 396 and set(sums) == {p.name for p in data.iterdir() if p.is_file() and p.name != 'SHA256SUMS'}
    assert all(pin(data / name)['sha256'] == value for name, value in sums.items())
    source_pins = dict(proof['source_pins'])
    for name in ['docs/literature/STRINGTIE_TABLES_DEVELOPMENT.md', 'tools/literature/freeze_stringtie_tables_development.py']:
        source_pins[name] = pin(ROOT / name)
    entries = {}

    def add(path, name):
        member = PurePosixPath(name)
        assert path.is_file() and not path.is_symlink() and name not in entries
        assert not member.is_absolute() and '..' not in member.parts and '\\' not in name
        entries[name] = path

    def tree(folder, prefix):
        assert folder.is_dir()
        for path in sorted(folder.rglob('*')):
            assert not path.is_symlink()
            if path.is_file(): add(path, prefix + '/' + path.relative_to(folder).as_posix())

    for name in source_pins: add(ROOT / name, 'source/' + name)
    for name in ['table-boundaries-1', 'table-wildcard-1', 'table-java-development-1', 'table-java-development-2']:
        tree(BASE / name, 'evidence/' + name)
    for path in sorted(matrix.rglob('*')):
        if path.is_file() and path.suffix != '.jar': add(path, 'evidence/native-table-matrix-1/' + path.relative_to(matrix).as_posix())
    for name in ['VALIDATION.json', 'SHA256SUMS']: add(parent / name, 'prior-native-GTF-checkpoint/' + name)
    for name in ['summary.json', 'tool-inventory.json', 'bigwig-role-coverage.json', 'SHA256SUMS']: add(data / name, 'unchanged-catalog/' + name)
    target.mkdir(); archive = target / 'controls.zip'; members = {}
    with zipfile.ZipFile(archive, 'x', compression=zipfile.ZIP_DEFLATED, compresslevel=9) as z:
        for name, path in sorted(entries.items()):
            raw = path.read_bytes(); info = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0)); info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = (stat.S_IFREG | 0o644) << 16; z.writestr(info, raw)
            members[name] = dict(bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest())
    with zipfile.ZipFile(archive) as z:
        assert z.testzip() is None and len(z.namelist()) == len(set(z.namelist())) == len(members)
        for name, value in members.items():
            raw = z.read(name); assert dict(bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest()) == value
    assert archive.stat().st_size < 95000000 and all(pin(ROOT / name) == value for name, value in source_pins.items())
    result = dict(proof, source_pins=source_pins, archive=dict(path=archive.name, **pin(archive), entries=len(members)), archive_files=members,
                  fresh_original_controls=fresh, previous_GTF_sources_and_artifact_unchanged=True, initial_failed_test_attempt_retained=True,
                  canonical_unchanged=True, third_party_executables_bundled=False,
                  unchanged_catalog=dict(new_scoped_integrations=67, including_existing=70, missing=260, reviewed_uses=819,
                                         checksummed_files=396, stringtie_status='missing'),
                  required_action='Read STRINGTIE_TABLES_DEVELOPMENT.md and STRINGTIE_DEVELOPMENT.md. This is a structure/reference-topology foundation, not a public StringTie integration. Retain original strands, every source table, omissions, numerical gene residuals and all earlier failures. Do not infer missing zeros, sum gene coverage naively, force TPM to one million or certify inference from structural checks. Complete edition-aware readback/normalization, alignment/count/coverage audits, exact runtime setup/admission, typed CLI and portable catalog/workflow gates. Missing/forbidden requirements mean skip dependent analysis with named recovery instructions. Failures admit no result; preserve attempts and retry corrected requests into NEW paths. Cell Ranger remains excluded.')
    (target / 'VALIDATION.json').write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8', newline='\n')
    (target / 'SHA256SUMS').write_text(''.join(pin(target / name)['sha256'] + '  ' + name + '\n' for name in ['VALIDATION.json', 'controls.zip']), encoding='ascii', newline='\n')
    (target / '.gitattributes').write_text('controls.zip -text\nVALIDATION.json -text\nSHA256SUMS -text\n', encoding='ascii', newline='\n')
    print(json.dumps({k: result[k] for k in ['status', 'archive', 'exact_new_source_classes', 'platform_validation', 'unchanged_catalog']}))


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable native table evidence checks')
    main()
