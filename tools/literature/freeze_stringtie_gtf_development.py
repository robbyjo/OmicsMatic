# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Freeze the native GTF foundation without promoting StringTie or changing the literature catalog."""
import argparse
import hashlib
import json
from pathlib import Path, PurePosixPath
import stat
import sys
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools/literature'))
from catalog_fixed_cohort import verify_fixed_cohort


def read(path):
    return json.loads(path.read_bytes())


def pin(path):
    with path.open('rb') as stream:
        return dict(bytes=path.stat().st_size, sha256=hashlib.file_digest(stream, 'sha256').hexdigest())


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--matrix', type=Path, required=True)
    args = parser.parse_args()
    matrix = args.matrix.resolve()
    base = ROOT / 'build/literature-review/stringtie-runtime'
    assert matrix.is_relative_to(base.resolve())
    target = ROOT / 'docs/literature/stringtie-gtf-development'
    assert not target.exists()
    proof = read(matrix / 'COMPLETED.json')
    assert not (matrix / 'INCOMPLETE.json').exists() and not (matrix / 'FAILURE.json').exists()
    assert proof['status'] == 'passed-exact-source-native-StringTie-GTF-structure-and-reference-audits'
    assert proof['exact_source_classes'] == len(proof['new_classes']) == 17 and proof['unchanged_parent_entries'] == 19529
    assert sorted(r['name'] for r in proof['platform_validation']) == ['linux-java21', 'windows-java21', 'windows-java25']
    assert all(r['tests'] == r['passed'] == 92 and r['skipped'] == 0 for r in proof['platform_validation'])
    assert proof['strict_Java17_source'] and proof['javadoc_passed'] and proof['original_GTF_protocols'] == 41
    assert not any(proof[k] for k in ['public_workflow_available', 'implementation_credit', 'released', 'pushed', 'deployed', 'whole_goal_complete'])
    assert all(pin(ROOT / name) == value for name, value in proof['source_pins'].items())
    assert all(pin(ROOT / name) == value for name, value in proof['dependency_pins'].items())
    assert pin(ROOT / proof['artifact']['path']) == {k: proof['artifact'][k] for k in ['bytes', 'sha256']}
    assert pin(ROOT / 'build/cli/omicsmatic-0.3.7.jar') == proof['canonical_executable_unchanged']
    with zipfile.ZipFile(ROOT / proof['parent_artifact']['path']) as old, zipfile.ZipFile(ROOT / proof['artifact']['path']) as new:
        assert set(new.namelist()) - set(old.namelist()) == set(proof['new_classes'])
        assert all(old.read(n) == new.read(n) for n in old.namelist())
        for name, value in proof['new_classes'].items():
            raw = new.read(name)
            assert dict(bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest()) == value

    intake = base / 'source-intake-1'
    controls = base / 'original-controls-1'
    original = read(intake / 'COMPLETED.json')
    captured = read(controls / 'CAPTURED.json')
    assert captured['original_intake'] == pin(intake / 'COMPLETED.json') and len(captured['editions']) == 7
    assert all(pin(intake / name) == {k: value[k] for k in ['bytes', 'sha256']} for name, value in original['files'].items())
    for edition in original['editions']:
        assert pin(intake / edition['executable']) == edition['executable_pin']
        assert all(pin(intake / name) == value for name, value in edition['licenses'].items())
    successful, failed = 0, []
    licenses = {}
    for edition in captured['editions']:
        assert edition['runtime']['exit_code'] == 0 and len(edition['cases']) == 7
        licenses[edition['version']] = edition['license']
        for case in edition['cases']:
            assert not case['process']['timed_out']
            assert all(pin(controls / edition['version'] / case['case'] / name) == value for name, value in case['files'].items())
            if case['process']['exit_code'] == 0:
                successful += 1
            else:
                assert case['process']['exit_code'] == 1
                failed.append(dict(version=edition['version'], case=case['case'], exit_code=1))
    assert successful == 41 and len(failed) == 8
    assert {r['version'] for r in failed if r['case'] == 'paper171-literal-rf'} == set(licenses)
    assert [r for r in failed if r['case'] != 'paper171-literal-rf'] == [dict(version='1.3.0', case='paper171-documented-rf', exit_code=1)]
    fixture = read(controls / 'FIXTURE.json')
    assert fixture['alignment_rows'] == 840 and fixture['spliced_rows'] == 120 and fixture['reference_transcripts'] == 4
    assert all(pin(controls / 'inputs' / name) == value for name, value in fixture['inputs'].items())
    alignment_census = read(base / 'original-alignment-census-1/VERIFIED.json')
    assert alignment_census['complete_alignment_rows'] == alignment_census['samtools_bam_rows'] == fixture['alignment_rows']
    assert alignment_census['spliced_rows'] == fixture['spliced_rows']
    assert alignment_census['bam'] == fixture['inputs']['reads.bam'] and alignment_census['sam'] == fixture['inputs']['reads.sam']
    census = read(base / 'primary-use-audit-1/PRIMARY-USES.json')
    assert census['papers_scanned'] == 250 and len(census['records']) == 8 and sum(len(r['matches']) for r in census['records']) == 13
    final_test = base / 'java-development-2/TEST-org.jlinalg.sequence.StringTieGtfTest.xml'
    test_xml = ET.fromstring(final_test.read_bytes())
    assert tuple(int(test_xml.get(k)) for k in ['tests', 'failures', 'errors', 'skipped']) == (92, 0, 0, 0)
    assert 'BUILD SUCCESSFUL' in (base / 'java-development-2/gradle.stdout').read_text()

    data = ROOT / 'docs/literature/omics-2023-2026'
    catalog = read(data / 'bigwig-catalog-validation.json')
    with zipfile.ZipFile(data / catalog['archive']['path']) as z:
        for name in ['papers.json', 'tool-inventory.json', 'summary.json', 'catalog-enrichment.json', 'WARNINGS.md']:
            assert (data / name).read_bytes() == z.read('catalog-snapshot/' + name)
    summary, papers = read(data / 'summary.json'), read(data / 'papers.json')
    verify_fixed_cohort(ROOT, data, papers)
    inventory = read(data / 'tool-inventory.json')
    assert next(t for t in inventory if t['id'] == 'stringtie')['implementation_status'] == 'missing'
    assert summary['implementation_status_counts']['native-subset-validated'] == 16 and summary['implementation_status_counts']['missing'] == 260
    assert hashlib.sha256((ROOT / 'tools/literature/reviewed-uses.json').read_bytes()).hexdigest() == read(data / 'bigwig-role-coverage.json')['reviews_sha256']
    catalog_sums = dict(line.split('  ', 1)[::-1] for line in (data / 'SHA256SUMS').read_text().splitlines())
    assert set(catalog_sums) == {p.name for p in data.iterdir() if p.is_file() and p.name != 'SHA256SUMS'}
    assert all(pin(data / name)['sha256'] == value for name, value in catalog_sums.items())

    sources = dict(proof['source_pins'])
    for name in ['docs/literature/STRINGTIE_DEVELOPMENT.md', 'tools/literature/audit_stringtie_paper_uses.py',
                 'tools/literature/intake_stringtie_reference.py', 'tools/literature/collect_stringtie_controls.py',
                 'tools/literature/freeze_stringtie_gtf_fixtures.py', 'tools/literature/freeze_stringtie_gtf_development.py']:
        sources[name] = pin(ROOT / name)
    entries = {}

    def add(path, name):
        relative = PurePosixPath(name)
        assert path.is_file() and not path.is_symlink() and name not in entries
        assert not relative.is_absolute() and '..' not in relative.parts and '\\' not in name
        entries[name] = path

    def tree(folder, prefix):
        assert folder.is_dir()
        for path in sorted(folder.rglob('*')):
            assert not path.is_symlink()
            if path.is_file():
                add(path, prefix + '/' + path.relative_to(folder).as_posix())

    for name in sources:
        add(ROOT / name, 'source/' + name)
    for name in ['primary-use-audit-1', 'original-controls-1', 'original-alignment-census-1', 'java-development-1', 'java-development-2', 'freeze-attempt-1']:
        tree(base / name, 'evidence/' + name)
    for path in sorted(matrix.rglob('*')):
        if path.is_file() and path.suffix != '.jar':
            add(path, 'evidence/native-gtf-matrix-1/' + path.relative_to(matrix).as_posix())
    for name in ['COMPLETED.json', 'PROGRESS.json', 'manual.html', 'history.html']:
        add(intake / name, 'official-intake/' + name)
    for edition in original['editions']:
        version = edition['version']
        name = version + '/source/stringtie-' + version + '/LICENSE'
        add(intake / name, 'official-intake/licenses/' + version + '.txt')
    for name in ['summary.json', 'tool-inventory.json', 'bigwig-role-coverage.json', 'SHA256SUMS']:
        add(data / name, 'unchanged-catalog/' + name)
    target.mkdir()
    archive = target / 'controls.zip'
    members = {}
    with zipfile.ZipFile(archive, 'x', compression=zipfile.ZIP_DEFLATED, compresslevel=9) as z:
        for name, path in sorted(entries.items()):
            raw = path.read_bytes()
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
    assert archive.stat().st_size < 95000000 and all(pin(ROOT / name) == value for name, value in sources.items())
    result = dict(proof, source_pins=sources, archive=dict(path=archive.name, **pin(archive), entries=len(members)), archive_files=members,
                  original_protocols=49, successful_original_protocols=41, retained_original_failures=failed,
                  original_licenses=licenses, primary_source_papers=8, named_primary_paragraphs=13,
                  source_census=pin(base / 'primary-use-audit-1/PRIMARY-USES.json'),
                  unchanged_catalog=dict(new_scoped_integrations=67, including_existing=70, missing=260, reviewed_uses=819,
                                         checksummed_files=len(catalog_sums), stringtie_status='missing'),
                  third_party_executables_bundled=False,
                  required_action='Read STRINGTIE_DEVELOPMENT.md. This is a native GTF structure/reference foundation, not an implemented StringTie workflow. Preserve literal paper171 -rf failures; documented --rf controls are separate. Keep edition-specific values and all omissions explicit. Supply exact permitted Linux requirements before dependent analysis; otherwise skip it with named recovery instructions. API/validation failures admit no document or validation result. Preserve failed/partial attempts, fix the cause and retry into NEW paths. Complete alignment, gene/Ballgown/inference audits, runtime setup/admission, typed CLI, portable verification and catalog gates before integration credit.')
    (target / 'VALIDATION.json').write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8', newline='\n')
    (target / 'SHA256SUMS').write_text(''.join(pin(target / name)['sha256'] + '  ' + name + '\n' for name in ['VALIDATION.json', 'controls.zip']), encoding='ascii', newline='\n')
    (target / '.gitattributes').write_text('controls.zip -text\nVALIDATION.json -text\nSHA256SUMS -text\n', encoding='ascii', newline='\n')
    print(json.dumps({k: result[k] for k in ['status', 'archive', 'exact_source_classes', 'platform_validation', 'successful_original_protocols', 'unchanged_catalog']}))


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable complete native GTF foundation evidence checks')
    main()
