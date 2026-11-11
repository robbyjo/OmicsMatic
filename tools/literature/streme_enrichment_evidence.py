# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Freeze or verify the bounded STREME statistical component and original discovery evidence."""
import argparse
import hashlib
import json
from pathlib import Path, PurePosixPath
import zipfile

ROOT = Path(__file__).resolve().parents[2]
BASE = ROOT / 'build/literature-review/meme-runtime'
DEST = ROOT / 'docs/literature/streme-enrichment-development'
FOLDERS = [
    'streme-enrichment-build-1', 'streme-enrichment-example-1', 'streme-enrichment-example-2',
    'historical-source-intake-1', 'historical-build-5.5.4-1', 'linux-build-3',
    'historical-build-4.9.1-base-1', 'historical-build-4.9.1-base-2', 'historical-build-4.9.1-base-3',
    'historical-build-4.9.1-base-4', 'historical-build-4.9.1-patch2-1', 'historical-build-4.9.1-patch2-2',
    'historical-build-4.9.1-patch2-3', 'historical-recovered-4.9.1-base-1', 'historical-recovered-4.9.1-patch2-1',
    'legacy-report-dependencies-1', 'discovery-original-5.5.4-1', 'discovery-original-5.5.9-1',
    'legacy-discovery-base-1', 'legacy-discovery-patch2-1', 'legacy-discovery-base-2', 'legacy-discovery-patch2-2',
]


def byte_pin(data):
    return dict(bytes=len(data), sha256=hashlib.sha256(data).hexdigest())


def pin(path):
    with path.open('rb') as stream:
        return dict(bytes=path.stat().st_size, sha256=hashlib.file_digest(stream, 'sha256').hexdigest())


def encoded(value):
    return (json.dumps(value, indent=2) + '\n').encode('utf-8')


def entries(path):
    with zipfile.ZipFile(path) as archive:
        assert len(archive.namelist()) == len(set(archive.namelist())) and archive.testzip() is None
        return {n: byte_pin(archive.read(n)) for n in archive.namelist()}


def freeze():
    assert not (DEST / 'VALIDATION.json').exists() and not (DEST / 'controls.zip').exists()
    validation = json.loads((BASE / 'streme-enrichment-build-1/COMPLETED.json').read_bytes())
    example = json.loads((BASE / 'streme-enrichment-example-2/COMPLETED.json').read_bytes())
    assert validation['status'] == 'passed-exact-STREME-Fisher-statistical-component'
    assert example['status'] == 'passed-native-STREME-statistical-example-three-JVMs'
    paths = [ROOT / n for n in validation['source_pins']]
    paths += [ROOT / n for n in example['source_pins']]
    paths += [ROOT / 'src/main/java/org/jlinalg/sequence/MemeXml.java', Path(__file__),
              ROOT / 'tools/literature/validate_mashr_native.py', ROOT / 'docs/literature/STREME_ENRICHMENT_DEVELOPMENT.md']
    paths += [ROOT / 'tools/literature' / n for n in [
        'build_meme_historical_reference.py', 'recover_meme_legacy_reference.py', 'prepare_meme_legacy_reports.py',
        'collect_meme_discovery_references.py', 'collect_meme_legacy_discovery.py']]
    sources = {p.relative_to(ROOT).as_posix(): pin(p) for p in sorted(set(paths))}
    for name, expected in validation['source_pins'].items():
        assert sources[name] == expected
    for name, expected in example['source_pins'].items():
        assert sources[name] == expected
    files = {'current-source/' + name: (ROOT / name).read_bytes() for name in sources}
    for folder in FOLDERS:
        base = BASE / folder
        assert base.is_dir(), folder
        for p in sorted(base.rglob('*')):
            if p.is_dir():
                continue
            assert not p.is_symlink()
            if p.suffix in {'.jar', '.gz'} or 'source-snapshot' in p.parts or 'test-classes' in p.parts:
                continue
            files['records/' + p.relative_to(ROOT).as_posix()] = p.read_bytes()
    parent = ROOT / validation['parent_artifact']['path']
    current = ROOT / validation['artifact']['path']
    assert pin(parent) == {k: validation['parent_artifact'][k] for k in ['bytes', 'sha256']}
    assert pin(current) == {k: validation['artifact'][k] for k in ['bytes', 'sha256']}
    old, new = entries(parent), entries(current)
    assert set(old) <= set(new) and all(new[n] == value for n, value in old.items())
    assert len(set(new) - set(old)) == 10
    for name, value in new.items():
        if name not in old:
            assert pin(BASE / 'streme-enrichment-build-1/scientific-classes' / name) == value
    files['artifact-entries/parent.json'] = encoded(old)
    files['artifact-entries/current.json'] = encoded(new)
    parent_proof = ROOT / 'docs/literature/htseq-tools-development/VALIDATION.json'
    files['parent/htseq-VALIDATION.json'] = parent_proof.read_bytes()
    assert len(files) < 10000 and sum(map(len, files.values())) < 128 * 1024**2
    with zipfile.ZipFile(DEST / 'controls.zip', 'x', zipfile.ZIP_DEFLATED) as archive:
        for name, data in sorted(files.items()):
            item = zipfile.ZipInfo(name, (2026, 11, 6, 0, 0, 0))
            item.compress_type = zipfile.ZIP_DEFLATED
            item.external_attr = 0o100644 << 16
            archive.writestr(item, data)
    proof = dict(schema_version=1, status='passed-scoped-STREME-enrichment-and-original-discovery-reference-checkpoint',
                 artifact=validation['artifact'], parent_artifact=validation['parent_artifact'], parent_validation=pin(parent_proof),
                 archive=pin(DEST / 'controls.zip'), warnings_document=pin(DEST / 'WARNINGS.md'),
                 source_pins=sources, logical_files={name: byte_pin(data) for name, data in sorted(files.items())},
                 logical_file_count=len(files), new_exact_source_classes=10, unchanged_parent_entries=len(old),
                 tests_per_JVM=2220, independent_exact_tables=2182, original_statistical_XML_documents=12,
                 checked_original_Fisher_tables=28, JVMs=['windows-java21', 'windows-java25', 'linux-java21'],
                 example_successful_demonstrations=6, example_named_unsupported_skips=3,
                 final_original_discovery_processes=46, final_original_exit0=42, final_original_failures=4,
                 earlier_legacy_calls=18, earlier_legacy_exit0=4, earlier_legacy_failures=14,
                 original_legacy_build_C_H_files_per_edition=646, original_legacy_C_H_unchanged=True,
                 original_legacy_pipeline_warning_gates_open=True, current_new_scoped_integrations=72,
                 current_integrations_including_existing=75, current_missing_named_tools=256,
                 canonical_executable_unchanged=validation['canonical_executable_unchanged'],
                 discovery_fit_available=False, public_discovery_execution_adapter_available=False,
                 site_and_score_audit_available=False, full_discovery_output_family_admitted=False,
                 author_runtime_identified=False, biological_validity_certified=False, execution_authenticated=False,
                 catalog_implementation_credit=False, released=False, pushed=False, deployed=False, whole_goal_complete=False)
    (DEST / 'VALIDATION.json').write_bytes(encoded(proof))


def verify():
    proof = json.loads((DEST / 'VALIDATION.json').read_bytes())
    assert proof['schema_version'] == 1 and proof['status'] == 'passed-scoped-STREME-enrichment-and-original-discovery-reference-checkpoint'
    assert pin(DEST / 'controls.zip') == proof['archive'] and pin(DEST / 'WARNINGS.md') == proof['warnings_document']
    with zipfile.ZipFile(DEST / 'controls.zip') as archive:
        assert len(archive.namelist()) == len(set(archive.namelist())) == proof['logical_file_count'] < 10000
        assert sum(x.file_size for x in archive.infolist()) < 128 * 1024**2 and archive.testzip() is None
        files = {}
        for name in archive.namelist():
            path = PurePosixPath(name)
            assert not path.is_absolute() and all(t not in {'', '.', '..'} for t in path.parts)
            files[name] = archive.read(name)
    assert {n: byte_pin(b) for n, b in files.items()} == proof['logical_files']
    for name, wanted in proof['source_pins'].items():
        assert pin(ROOT / name) == wanted == byte_pin(files['current-source/' + name]), name
    def record(folder, name='COMPLETED.json'):
        return json.loads(files['records/build/literature-review/meme-runtime/' + folder + '/' + name])
    v = record('streme-enrichment-build-1')
    assert (v['independent_exact_tables'], v['independently_checked_original_tables']) == (2182, 28)
    assert len(v['runs']) == 3 and all((r['tests'], r['succeeded'], r['skipped']) == (2220, 2220, 0) for r in v['runs'])
    e = record('streme-enrichment-example-2')
    assert e['successful_demonstrations'] == 6 and e['named_unsupported_skips'] == 3
    assert len(e['processes']) == 10 and all(p['exit_code'] == p['expected_exit'] for p in e['processes'])
    final = [record(n) for n in ['discovery-original-5.5.4-1', 'discovery-original-5.5.9-1', 'legacy-discovery-base-2', 'legacy-discovery-patch2-2']]
    assert all(x['unchanged_inputs_and_runtime'] for x in final)
    assert sum(len(x['processes']) for x in final) == 46 and sum(x['successful_processes'] for x in final) == 42
    prior = [record(n) for n in ['legacy-discovery-base-1', 'legacy-discovery-patch2-1']]
    assert sum(len(x['processes']) for x in prior) == 18 and sum(x['successful_processes'] for x in prior) == 4
    for n in ['historical-recovered-4.9.1-base-1', 'historical-recovered-4.9.1-patch2-1']:
        r = record(n)
        assert r['status'] == 'built-original-historical-MEME-reference-not-scientifically-admitted'
        assert r['unchanged_original_C_and_H'] == len(r['original_C_and_H']) == 646
        assert len(r['installed_files']) == 487 and not r['installed_symlinks']
    old, new = (json.loads(files['artifact-entries/' + n + '.json']) for n in ['parent', 'current'])
    assert set(old) <= set(new) and all(new[n] == value for n, value in old.items()) and len(set(new) - set(old)) == 10
    for name in set(new) - set(old):
        assert new[name] == byte_pin(files['records/build/literature-review/meme-runtime/streme-enrichment-build-1/scientific-classes/' + name])
    assert byte_pin(files['parent/htseq-VALIDATION.json']) == proof['parent_validation']
    for a in [proof['artifact'], proof['parent_artifact']]:
        path = ROOT / a['path']
        if path.exists():
            assert pin(path) == {k: a[k] for k in ['bytes', 'sha256']}
    assert pin(ROOT / 'build/cli/omicsmatic-0.3.7.jar') == proof['canonical_executable_unchanged']
    assert all(proof[k] is False for k in ['catalog_implementation_credit', 'released', 'pushed', 'deployed', 'whole_goal_complete', 'full_discovery_output_family_admitted'])
    print(json.dumps(dict(status='verified-STREME-statistical-component-and-retained-discovery-references',
          sources=len(proof['source_pins']), logical_files=len(files), archive=proof['archive'],
          tests_per_JVM=2220, original_final_calls=46, original_exit0=42, catalog_credit=False)))


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable evidence identity checks')
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--freeze', action='store_true')
    args = parser.parse_args()
    if args.freeze:
        freeze()
    verify()
