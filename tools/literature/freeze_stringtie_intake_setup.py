# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Freeze complete BAM intake and actual standalone setup/export evidence without fitting credit."""
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
    target = ROOT / 'docs/literature/stringtie-intake-setup-development'; assert not target.exists()
    matrix = BASE / 'native-intake-setup-matrix-1'; proof = read(matrix / 'COMPLETED.json')
    assert proof['status'] == 'passed-native-complete-StringTie-BAM-intake-and-portable-exact-setup-export'
    assert not (matrix / 'INCOMPLETE.json').exists() and not (matrix / 'FAILURE.json').exists()
    assert proof['exact_new_source_classes'] == len(proof['new_classes']) == 14 and len(proof['new_resources']) == 13 and proof['unchanged_parent_entries'] == 19571
    assert sorted(r['name'] for r in proof['platform_validation']) == ['linux-java21', 'windows-java21', 'windows-java25']
    assert all(r['tests'] == r['passed'] == 1307 and r['skipped'] == 0 and r['actual_main_kit_export'] for r in proof['platform_validation'])
    assert proof['actual_main_exports'] == 3 and proof['standalone_export_install_passed'] and proof['public_setup_export_available']
    assert proof['strict_Java17_source'] and proof['javadoc_passed']
    assert not any(proof[k] for k in ['public_fitting_workflow_available', 'scientific_runtime_admitted', 'user_analysis_admitted', 'implementation_credit', 'released', 'pushed', 'deployed', 'whole_goal_complete'])
    assert all(pin(ROOT / name) == value for name, value in proof['source_pins'].items()) and all(pin(ROOT / name) == value for name, value in proof['dependency_pins'].items())
    assert pin(ROOT / proof['artifact']['path']) == {k: proof['artifact'][k] for k in ['bytes', 'sha256']}
    assert pin(ROOT / 'build/cli/omicsmatic-0.3.7.jar') == proof['canonical_executable_unchanged']
    parent = ROOT / 'docs/literature/stringtie-normalization-development'
    assert pin(parent / 'VALIDATION.json') == proof['parent_validation'] and pin(parent / 'controls.zip') == proof['parent_archive']
    previous = read(parent / 'VALIDATION.json'); assert all(pin(ROOT / n) == v for n, v in previous['source_pins'].items())
    with zipfile.ZipFile(ROOT / proof['parent_artifact']['path']) as old, zipfile.ZipFile(ROOT / proof['artifact']['path']) as new:
        assert new.testzip() is None and len(new.namelist()) == len(set(new.namelist()))
        assert set(new.namelist()) - set(old.namelist()) == set(proof['new_classes']) | set(proof['new_resources']) and all(new.read(n) == old.read(n) for n in old.namelist())
        for n, v in {**proof['new_classes'], **proof['new_resources']}.items():
            raw = new.read(n); assert dict(bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest()) == v
    setup = BASE / 'setup-validation-1'; setup_proof = read(setup / 'COMPLETED.json')
    assert pin(setup / 'COMPLETED.json') == proof['setup_validation'] and setup_proof['offline_installs'] == setup_proof['unchanged_payload_rechecks'] == 7
    assert setup_proof['actual_permission_skip_uid'] == 65534 and setup_proof['download_status'] == 'passed-two-exact-official-downloads-and-private-install'
    assert all(pin(ROOT / n) == v for n, v in setup_proof['sources'].items()) and not setup_proof['scientific_runtime_admitted'] and not setup_proof['user_analysis_admitted']
    assert len(setup_proof['homes']) == 7 and len(setup_proof['cases']) == 23
    for case in setup_proof['cases']:
        process = case['process']; assert not process['timed_out']
        if 'expected_exit' in case: assert process['exit_code'] == case['expected_exit']
    for profile in setup_proof['homes']:
        for name in [Path(profile['setup_report']).name, Path(profile['recheck_report']).name]:
            folder = setup / 'reports' / name; manifest = read(folder / 'manifest.json'); completion = read(folder / 'COMPLETED.json')
            assert completion['manifest'] == pin(folder / 'manifest.json') and not (folder / 'INCOMPLETE.json').exists()
            assert all(pin(folder / n) == v for n, v in manifest['files'].items())
            assert not manifest['scientific_runtime_admitted'] and not manifest['user_analysis_admitted']
    standalone = read(matrix / 'STANDALONE.json'); assert standalone['exit_code'] == 0 and not standalone['repository_code_imports_required'] and not standalone['scientific_runtime_admitted'] and not standalone['user_analysis_admitted']
    for platform in ['windows-java21', 'windows-java25', 'linux-java21']:
        folder = matrix / ('export-' + platform); export = read(folder / 'export.json')
        assert len(export['files']) == 14 and not export['installation_performed'] and all(pin(folder / n) == v for n, v in export['files'].items())
        assert set(p.relative_to(folder).as_posix() for p in folder.rglob('*') if p.is_file()) == set(export['files']) | {'export.json'}
    for name, expected in [('org.jlinalg.cli.StringTieBamTest', 47), ('org.jlinalg.cli.StringTieSetupExportTest', 4), ('org.jlinalg.sequence.StringTieGtfTest', 92),
                           ('org.jlinalg.sequence.StringTieTablesTest', 99), ('org.jlinalg.sequence.StringTieNormalizationTest', 1065)]:
        row = ET.fromstring((BASE / 'intake-setup-java-development-1' / ('TEST-' + name + '.xml')).read_bytes())
        assert tuple(int(row.get(k)) for k in ['tests', 'failures', 'errors', 'skipped']) == (expected, 0, 0, 0)
    assert read(BASE / 'bam-java-development-1/FAILURE.json')['status'] == 'compile-failed-no-tests-run'
    assert not list((BASE / 'bam-java-development-1').glob('TEST-*.xml'))
    data = ROOT / 'docs/literature/omics-2023-2026'; catalog = read(data / 'bigwig-catalog-validation.json')
    with zipfile.ZipFile(data / catalog['archive']['path']) as z:
        for name in ['papers.json', 'tool-inventory.json', 'summary.json', 'catalog-enrichment.json', 'WARNINGS.md']: assert (data / name).read_bytes() == z.read('catalog-snapshot/' + name)
    assert next(t for t in read(data / 'tool-inventory.json') if t['id'] == 'stringtie')['implementation_status'] == 'missing'
    assert pin(ROOT / 'tools/literature/reviewed-uses.json')['sha256'] == read(data / 'bigwig-role-coverage.json')['reviews_sha256']
    sums = dict(line.split('  ', 1)[::-1] for line in (data / 'SHA256SUMS').read_text().splitlines()); assert len(sums) == 396
    assert set(sums) == {p.name for p in data.iterdir() if p.is_file() and p.name != 'SHA256SUMS'} and all(pin(data / n)['sha256'] == v for n, v in sums.items())
    sources = dict(proof['source_pins'])
    for n in ['docs/literature/STRINGTIE_INTAKE_SETUP_DEVELOPMENT.md', 'tools/literature/freeze_stringtie_intake_setup.py']: sources[n] = pin(ROOT / n)
    entries = {}

    def add(path, name):
        p = PurePosixPath(name); assert path.is_file() and not path.is_symlink() and name not in entries and not p.is_absolute() and '..' not in p.parts and '\\' not in name
        entries[name] = path

    def tree(folder, prefix):
        assert folder.is_dir()
        for path in sorted(folder.rglob('*')):
            assert not path.is_symlink()
            if path.is_file() and path.suffix not in ('.jar', '.part'): add(path, prefix + '/' + path.relative_to(folder).as_posix())

    for n in sources: add(ROOT / n, 'source/' + n)
    for name in ['setup-validation-1', 'bam-java-development-1', 'bam-java-development-2', 'bam-java-development-3', 'intake-setup-java-development-1', 'native-intake-setup-matrix-1']: tree(BASE / name, 'evidence/' + name)
    for name in ['VALIDATION.json', 'SHA256SUMS']: add(parent / name, 'prior-normalization-checkpoint/' + name)
    for name in ['summary.json', 'tool-inventory.json', 'bigwig-role-coverage.json', 'SHA256SUMS']: add(data / name, 'unchanged-catalog/' + name)
    target.mkdir(); archive = target / 'controls.zip'; members = {}
    with zipfile.ZipFile(archive, 'x', compression=zipfile.ZIP_DEFLATED, compresslevel=9) as z:
        for n, path in sorted(entries.items()):
            raw = path.read_bytes(); info = zipfile.ZipInfo(n, (1980, 1, 1, 0, 0, 0)); info.compress_type = zipfile.ZIP_DEFLATED; info.external_attr = (stat.S_IFREG | 0o644) << 16
            z.writestr(info, raw); members[n] = dict(bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest())
    with zipfile.ZipFile(archive) as z:
        assert z.testzip() is None and len(z.namelist()) == len(set(z.namelist())) == len(members)
        for n, v in members.items():
            raw = z.read(n); assert dict(bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest()) == v
    assert archive.stat().st_size < 95000000 and all(pin(ROOT / n) == v for n, v in sources.items())
    result = dict(proof, source_pins=sources, archive=dict(path=archive.name, **pin(archive), entries=len(members)), archive_files=members,
                  setup_controls=setup_proof['cases'], retained_Linux_setup_base=setup_proof['linux_base'], retained_exact_profile_homes=setup_proof['homes'], standalone_install=standalone,
                  initial_compile_failure_retained_and_stale_XML_explicit=True, previous_scientific_sources_and_artifacts_unchanged=True, canonical_unchanged=True,
                  third_party_executables_bundled=False, unchanged_catalog=dict(new_scoped_integrations=67, including_existing=70, missing=260, reviewed_uses=819, checksummed_files=396, stringtie_status='missing'),
                  required_action='Read STRINGTIE_INTAKE_SETUP_DEVELOPMENT.md and exported RUN-SETUP.txt. Use the exact development JAR and edition. Export only copies a kit; setup only verifies original executable/license bytes. Require exact completion manifests with no INCOMPLETE/FAILURE markers. Current-host ELF/library and fresh health admission, typed assembly/estimation/merge fits and portable complete fit verification remain required before integration credit. Missing/forbidden install/download/access/filesystem/runtime requirements mean SKIP3 and skip dependent analysis with named recovery. Corruption/invalid input/process/capacity/cancellation/finalization means FAIL2 and no result. Preserve all attempts, private Linux homes, original data and explicit NH/XS policies; retry corrected requests into NEW paths. No filtering, mate reciprocity, gene inference or biological accuracy is certified. Cell Ranger remains excluded.')
    (target / 'VALIDATION.json').write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8', newline='\n')
    (target / 'SHA256SUMS').write_text(''.join(pin(target / n)['sha256'] + '  ' + n + '\n' for n in ['VALIDATION.json', 'controls.zip']), encoding='ascii', newline='\n')
    (target / '.gitattributes').write_text('controls.zip -text\nVALIDATION.json -text\nSHA256SUMS -text\n', encoding='ascii', newline='\n')
    print(json.dumps({k: result[k] for k in ['status', 'archive', 'exact_new_source_classes', 'platform_validation', 'actual_main_exports', 'standalone_export_install_passed', 'unchanged_catalog']}))


if __name__ == '__main__':
    if not __debug__: raise RuntimeError('Never disable complete BAM/setup evidence checks')
    main()
