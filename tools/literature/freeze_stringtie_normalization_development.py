# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Freeze exact edition arithmetic/conditional normalization evidence without whole-tool or integration overclaim."""
import hashlib
import json
from pathlib import Path, PurePosixPath
import stat
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[2]
BASE = ROOT / 'build/literature-review/stringtie-runtime'


def read(path): return json.loads(path.read_bytes())


def pin(path):
    with path.open('rb') as stream: return dict(bytes=path.stat().st_size, sha256=hashlib.file_digest(stream, 'sha256').hexdigest())


def main():
    target = ROOT / 'docs/literature/stringtie-normalization-development'; assert not target.exists()
    matrix = BASE / 'native-normalization-matrix-1'; proof = read(matrix / 'COMPLETED.json')
    assert proof['status'] == 'passed-native-StringTie-edition-finalization-and-conservative-transcript-normalization-audits'
    assert not (matrix / 'INCOMPLETE.json').exists() and not (matrix / 'FAILURE.json').exists()
    assert proof['exact_new_source_classes'] == len(proof['new_classes']) == 11 and proof['unchanged_parent_entries'] == 19560
    assert sorted(r['name'] for r in proof['platform_validation']) == ['linux-java21', 'windows-java21', 'windows-java25']
    assert all(r['tests'] == r['passed'] == 1256 and r['skipped'] == 0 for r in proof['platform_validation'])
    assert proof['extracted_source_arithmetic_cases'] == 1008 and proof['whole_tool_table_families'] == 35 and proof['new_whole_StringTie_runs'] == 0
    assert proof['strict_Java17_source'] and proof['javadoc_passed']
    assert not any(proof[k] for k in ['public_workflow_available', 'implementation_credit', 'exact_hidden_denominators_recovered',
                                    'gene_inference_certified', 'biological_accuracy_certified', 'released', 'pushed', 'deployed', 'whole_goal_complete'])
    assert all(pin(ROOT / name) == value for name, value in proof['source_pins'].items())
    assert all(pin(ROOT / name) == value for name, value in proof['dependency_pins'].items())
    assert pin(ROOT / proof['artifact']['path']) == {k: proof['artifact'][k] for k in ['bytes', 'sha256']}
    assert pin(ROOT / 'build/cli/omicsmatic-0.3.7.jar') == proof['canonical_executable_unchanged']
    parent = ROOT / 'docs/literature/stringtie-tables-development'
    assert pin(parent / 'VALIDATION.json') == proof['parent_validation'] and pin(parent / 'controls.zip') == proof['parent_archive']
    previous = read(parent / 'VALIDATION.json'); assert all(pin(ROOT / name) == value for name, value in previous['source_pins'].items())
    with zipfile.ZipFile(ROOT / proof['parent_artifact']['path']) as old, zipfile.ZipFile(ROOT / proof['artifact']['path']) as new:
        assert new.testzip() is None and len(new.namelist()) == len(set(new.namelist()))
        assert set(new.namelist()) - set(old.namelist()) == set(proof['new_classes']) and all(old.read(n) == new.read(n) for n in old.namelist())
        for name, value in proof['new_classes'].items():
            raw = new.read(name); assert dict(bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest()) == value
    controls = BASE / 'normalization-source-controls-1'; capture = read(controls / 'CAPTURED.json')
    assert capture['total_cases'] == 1008 and len(capture['editions']) == 7 and not capture['whole_StringTie_runs'] and not capture['implementation_credit']
    assert pin(ROOT / 'tools/literature/collect_stringtie_normalization.py') == capture['collector'] and pin(controls / 'cases.tsv') == capture['cases']
    for edition in capture['editions']:
        folder = controls / edition['version']
        assert edition['cases'] == 144 and edition['process']['exit_code'] == edition['build']['exit_code'] == 0
        assert pin(folder / 'arithmetic-control') == edition['binary'] and pin(folder / 'original-values.tsv') == edition['outputs']
        assert pin(folder / 'original-expressions.cpp.txt') == edition['expression'] and pin(folder / 'driver.cpp') == edition['driver']
        assert pin(ROOT / edition['source_relative']) == edition['source']
    fixture = ROOT / 'src/test/resources/org/jlinalg/sequence/stringtie-normalization'; fixture_proof = read(fixture / 'CAPTURE.json')
    assert fixture_proof['cases'] == 1008 and not fixture_proof['whole_StringTie_runs'] and pin(fixture / 'original-normalization-arithmetic.zip') == fixture_proof['archive']
    with zipfile.ZipFile(fixture / 'original-normalization-arithmetic.zip') as z:
        assert z.testzip() is None and len(z.namelist()) == len(set(z.namelist())) == len(fixture_proof['archive_files']) == 65
        for name, value in fixture_proof['archive_files'].items():
            raw = z.read(name); assert dict(bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest()) == value
    for name, expected in [('StringTieGtfTest', 92), ('StringTieTablesTest', 99), ('StringTieNormalizationTest', 1065)]:
        test = ET.fromstring((BASE / 'normalization-java-development-2' / ('TEST-org.jlinalg.sequence.' + name + '.xml')).read_bytes())
        assert tuple(int(test.get(k)) for k in ['tests', 'failures', 'errors', 'skipped']) == (expected, 0, 0, 0)
    assert 'BUILD SUCCESSFUL' in (BASE / 'normalization-java-development-2/gradle.stdout').read_text()
    assert read(BASE / 'normalization-java-development-1/FAILURE.json')['failed'] == 1
    data = ROOT / 'docs/literature/omics-2023-2026'; catalog = read(data / 'bigwig-catalog-validation.json')
    with zipfile.ZipFile(data / catalog['archive']['path']) as z:
        for name in ['papers.json', 'tool-inventory.json', 'summary.json', 'catalog-enrichment.json', 'WARNINGS.md']: assert (data / name).read_bytes() == z.read('catalog-snapshot/' + name)
    assert next(t for t in read(data / 'tool-inventory.json') if t['id'] == 'stringtie')['implementation_status'] == 'missing'
    assert pin(ROOT / 'tools/literature/reviewed-uses.json')['sha256'] == read(data / 'bigwig-role-coverage.json')['reviews_sha256']
    sums = dict(line.split('  ', 1)[::-1] for line in (data / 'SHA256SUMS').read_text().splitlines())
    assert len(sums) == 396 and set(sums) == {p.name for p in data.iterdir() if p.is_file() and p.name != 'SHA256SUMS'}
    assert all(pin(data / name)['sha256'] == value for name, value in sums.items())
    sources = dict(proof['source_pins'])
    for name in ['docs/literature/STRINGTIE_NORMALIZATION_DEVELOPMENT.md', 'tools/literature/freeze_stringtie_normalization_development.py']: sources[name] = pin(ROOT / name)
    entries = {}

    def add(path, name):
        member = PurePosixPath(name)
        assert path.is_file() and not path.is_symlink() and name not in entries and not member.is_absolute() and '..' not in member.parts and '\\' not in name
        entries[name] = path

    def tree(folder, prefix):
        assert folder.is_dir()
        for path in sorted(folder.rglob('*')):
            assert not path.is_symlink()
            if path.is_file() and path.name != 'arithmetic-control' and path.suffix != '.jar': add(path, prefix + '/' + path.relative_to(folder).as_posix())

    for name in sources: add(ROOT / name, 'source/' + name)
    for name in ['normalization-source-controls-1', 'normalization-java-development-1', 'normalization-java-development-2', 'native-normalization-matrix-1']: tree(BASE / name, 'evidence/' + name)
    for name in ['VALIDATION.json', 'SHA256SUMS']: add(parent / name, 'prior-table-checkpoint/' + name)
    for name in ['summary.json', 'tool-inventory.json', 'bigwig-role-coverage.json', 'SHA256SUMS']: add(data / name, 'unchanged-catalog/' + name)
    target.mkdir(); archive = target / 'controls.zip'; members = {}
    with zipfile.ZipFile(archive, 'x', compression=zipfile.ZIP_DEFLATED, compresslevel=9) as z:
        for name, path in sorted(entries.items()):
            raw = path.read_bytes(); info = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0)); info.compress_type = zipfile.ZIP_DEFLATED; info.external_attr = (stat.S_IFREG | 0o644) << 16
            z.writestr(info, raw); members[name] = dict(bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest())
    with zipfile.ZipFile(archive) as z:
        assert z.testzip() is None and len(z.namelist()) == len(set(z.namelist())) == len(members)
        for name, value in members.items():
            raw = z.read(name); assert dict(bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest()) == value
    assert archive.stat().st_size < 95000000 and all(pin(ROOT / name) == value for name, value in sources.items())
    result = dict(proof, source_pins=sources, archive=dict(path=archive.name, **pin(archive), entries=len(members)), archive_files=members,
                  whole_tool_and_extracted_expression_evidence_distinguished=True, failed_synthetic_rounding_test_retained=True,
                  previous_GTF_and_table_sources_and_artifacts_unchanged=True, canonical_unchanged=True, third_party_executables_bundled=False,
                  unchanged_catalog=dict(new_scoped_integrations=67, including_existing=70, missing=260, reviewed_uses=819, checksummed_files=396, stringtie_status='missing'),
                  required_action='Read STRINGTIE_NORMALIZATION_DEVELOPMENT.md and prior scope guides. Select an exact edition, preserve every original/nonfinite value and omitted identity, and distinguish source-expression controls from whole-tool fits. Denominator bounds are conservative conditional compatibility ranges, not recovered truth or confidence intervals. Unresolved zero/omitted cases do not support dependent interpretations requiring an identified factor. Complete independent alignment/count/coverage and gene checks where supportable, exact runtime setup/admission, typed CLI, portable workflow verification and catalog gates. Forbidden/unavailable requirements mean skip dependent analysis with named recovery steps. Failures admit no result; retain attempts and retry corrected requests into NEW paths. Cell Ranger remains excluded.')
    (target / 'VALIDATION.json').write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8', newline='\n')
    (target / 'SHA256SUMS').write_text(''.join(pin(target / name)['sha256'] + '  ' + name + '\n' for name in ['VALIDATION.json', 'controls.zip']), encoding='ascii', newline='\n')
    (target / '.gitattributes').write_text('controls.zip -text\nVALIDATION.json -text\nSHA256SUMS -text\n', encoding='ascii', newline='\n')
    print(json.dumps({k: result[k] for k in ['status', 'archive', 'exact_new_source_classes', 'platform_validation', 'unchanged_catalog']}))


if __name__ == '__main__':
    if not __debug__: raise RuntimeError('Never disable complete original-edition normalization evidence checks')
    main()
