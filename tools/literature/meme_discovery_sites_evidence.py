# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Freeze or verify complete native MEME site/matrix/diagram component evidence without catalog credit."""
import argparse
import hashlib
import json
from pathlib import Path, PurePosixPath
import zipfile

ROOT = Path(__file__).resolve().parents[2]
BASE = ROOT / 'build/literature-review/meme-runtime'
DEST = ROOT / 'docs/literature/meme-discovery-sites-development'
FOLDERS = ['sites-original-4.9.1-base-1', 'sites-original-4.9.1-patch2-1', 'sites-original-5.5.4-1', 'sites-original-5.5.9-1',
           'site-independent-audit-1', 'site-independent-audit-2', 'discovery-sites-build-1', 'discovery-sites-build-2', 'discovery-sites-example-1']


def pin(path):
    with path.open('rb') as stream:
        return dict(bytes=path.stat().st_size, sha256=hashlib.file_digest(stream, 'sha256').hexdigest())


def byte_pin(data):
    return dict(bytes=len(data), sha256=hashlib.sha256(data).hexdigest())


def encoded(value):
    return (json.dumps(value, indent=2) + '\n').encode('utf-8')


def entries(path):
    with zipfile.ZipFile(path) as archive:
        assert len(archive.namelist()) == len(set(archive.namelist())) and archive.testzip() is None
        return {n: byte_pin(archive.read(n)) for n in archive.namelist()}


def freeze():
    assert not (DEST / 'VALIDATION.json').exists() and not (DEST / 'controls.zip').exists()
    v = json.loads((BASE / 'discovery-sites-build-2/COMPLETED.json').read_bytes())
    e = json.loads((BASE / 'discovery-sites-example-1/COMPLETED.json').read_bytes())
    assert v['status'] == 'passed-exact-source-MEME-discovery-site-and-scoring-components'
    assert e['status'] == 'passed-portable-MEME-site-example-three-JVMs'
    source_names = set(v['source_pins']) | set(e['source_pins'])
    source_names |= {'src/main/java/org/jlinalg/sequence/MemeXml.java', 'tools/literature/meme_discovery_sites_evidence.py',
                     'tools/literature/validate_mashr_native.py', 'tools/literature/collect_meme_discovery_references.py',
                     'docs/literature/MEME_DISCOVERY_SITES_DEVELOPMENT.md'}
    sources = {name: pin(ROOT / name) for name in sorted(source_names)}
    for parent in [v, e]:
        assert all(sources[name] == wanted for name, wanted in parent['source_pins'].items())
    files = {'current-source/' + n: (ROOT / n).read_bytes() for n in sources}
    for name in FOLDERS:
        folder = BASE / name
        assert folder.is_dir()
        for p in sorted(folder.rglob('*')):
            if p.is_dir():
                continue
            assert not p.is_symlink()
            if p.suffix == '.jar' or 'source-snapshot' in p.parts or 'test-classes' in p.parts:
                continue
            files['records/' + p.relative_to(ROOT).as_posix()] = p.read_bytes()
    old_path, new_path = ROOT / v['parent_artifact']['path'], ROOT / v['artifact']['path']
    assert pin(old_path) == {k: v['parent_artifact'][k] for k in ['bytes', 'sha256']}
    assert pin(new_path) == {k: v['artifact'][k] for k in ['bytes', 'sha256']}
    old, new = entries(old_path), entries(new_path)
    assert set(old) <= set(new) and all(new[n] == p for n, p in old.items()) and len(set(new) - set(old)) == 19
    for n in set(new) - set(old):
        assert pin(BASE / 'discovery-sites-build-2/scientific-classes' / n) == new[n]
    files['artifact-entries/parent.json'] = encoded(old); files['artifact-entries/current.json'] = encoded(new)
    parent_path = ROOT / 'docs/literature/streme-enrichment-development/VALIDATION.json'
    parent_archive = parent_path.with_name('controls.zip')
    files['parent/streme-VALIDATION.json'] = parent_path.read_bytes()
    assert len(files) < 10000 and sum(map(len, files.values())) < 128 * 1024**2
    with zipfile.ZipFile(DEST / 'controls.zip', 'x', zipfile.ZIP_DEFLATED) as archive:
        for name, data in sorted(files.items()):
            item = zipfile.ZipInfo(name, (2026, 11, 6, 0, 0, 0)); item.compress_type = zipfile.ZIP_DEFLATED; item.external_attr = 0o100644 << 16
            archive.writestr(item, data)
    proof = dict(schema_version=1, status='passed-scoped-native-MEME-discovery-site-and-scoring-checkpoint',
                 artifact=v['artifact'], parent_artifact=v['parent_artifact'], parent_validation=pin(parent_path), parent_archive=pin(parent_archive),
                 archive=pin(DEST / 'controls.zip'), warnings_document=pin(DEST / 'WARNINGS.md'), source_pins=sources,
                 logical_file_count=len(files), logical_files={n: byte_pin(data) for n, data in sorted(files.items())},
                 tests_per_JVM=94, JVMs=['windows-java21', 'windows-java25', 'linux-java21'], exact_new_classes=19,
                 unchanged_parent_entries=len(old), original_discovery_families=42, original_motifs=84,
                 original_contributing_sites=4136, original_sequence_diagrams=3040, original_selected_hits=892,
                 complete_independent_null_tail_cells=64284, original_runtime_profiles=['4.9.1-base', '4.9.1-patch2', '5.5.4', '5.5.9'],
                 additional_original_calls=16, additional_original_exit0=16, independent_exact_integer_polynomials=True,
                 raw_FASTA_WEIGHTS_background_bindings_checked=True, scientific_mutation_refusals=41,
                 successful_portable_examples=3, preserved_existing_output_refusals=3,
                 canonical_executable_unchanged=v['canonical_executable_unchanged'],
                 current_new_scoped_integrations=72, current_integrations_including_existing=75, current_missing_named_tools=256,
                 native_EM_fit_available=False, motif_significance_recomputed=False, full_command_bound=False,
                 full_report_family_admitted=False, public_discovery_execution_adapter_available=False,
                 combined_legacy_MEME_ChIP_report_warnings_resolved=False, author_runtime_identified=False,
                 biological_validity_certified=False, execution_authenticated=False, catalog_implementation_credit=False,
                 released=False, pushed=False, deployed=False, whole_goal_complete=False)
    (DEST / 'VALIDATION.json').write_bytes(encoded(proof))


def verify():
    proof = json.loads((DEST / 'VALIDATION.json').read_bytes())
    assert proof['schema_version'] == 1 and proof['status'] == 'passed-scoped-native-MEME-discovery-site-and-scoring-checkpoint'
    assert pin(DEST / 'controls.zip') == proof['archive'] and pin(DEST / 'WARNINGS.md') == proof['warnings_document']
    with zipfile.ZipFile(DEST / 'controls.zip') as archive:
        assert len(archive.namelist()) == len(set(archive.namelist())) == proof['logical_file_count'] < 10000 and archive.testzip() is None
        assert sum(x.file_size for x in archive.infolist()) < 128 * 1024**2
        files = {}
        for name in archive.namelist():
            path = PurePosixPath(name); assert not path.is_absolute() and all(t not in {'', '.', '..'} for t in path.parts)
            files[name] = archive.read(name)
    assert {n: byte_pin(b) for n, b in files.items()} == proof['logical_files']
    for n, p in proof['source_pins'].items():
        assert pin(ROOT / n) == p == byte_pin(files['current-source/' + n]), n
    def record(folder, name='COMPLETED.json'):
        return json.loads(files['records/build/literature-review/meme-runtime/' + folder + '/' + name])
    v = record('discovery-sites-build-2'); assert len(v['runs']) == 3
    assert all((r['tests'], r['succeeded'], r['skipped']) == (94, 94, 0) for r in v['runs'])
    a = record('site-independent-audit-2')
    assert (a['original_families'], a['contributing_sites'], a['scanned_hits']) == (42, 4136, 892)
    assert sum(len(f['motifs']) for f in a['families']) == 84 and sum(len(f['scans']) for f in a['families']) == 3040
    assert sum(len(m['score_model']['tails']) for f in a['families'] for m in f['motifs']) == 64284
    for folder in FOLDERS[:4]:
        row = record(folder); assert row['successful_processes'] == len(row['processes']) == 4 and row['unchanged_inputs_and_runtime']
    e = record('discovery-sites-example-1'); assert (e['successful_demonstrations'], e['existing_output_refusals']) == (3, 3)
    assert len(e['processes']) == 7 and all(p['exit_code'] == p['expected_exit'] for p in e['processes'])
    assert record('discovery-sites-build-1', 'FAILURE.json')['status'] == 'failed-MEME-discovery-site-component-validation-no-result-admitted'
    old, new = (json.loads(files['artifact-entries/' + n + '.json']) for n in ['parent', 'current'])
    assert set(old) <= set(new) and all(new[n] == p for n, p in old.items()) and len(set(new) - set(old)) == 19
    for n in set(new) - set(old):
        assert new[n] == byte_pin(files['records/build/literature-review/meme-runtime/discovery-sites-build-2/scientific-classes/' + n])
    parent_path = ROOT / 'docs/literature/streme-enrichment-development/VALIDATION.json'
    assert pin(parent_path) == proof['parent_validation'] == byte_pin(files['parent/streme-VALIDATION.json'])
    assert pin(parent_path.with_name('controls.zip')) == proof['parent_archive']
    for artifact in [proof['artifact'], proof['parent_artifact']]:
        path = ROOT / artifact['path']
        if path.exists():
            assert pin(path) == {k: artifact[k] for k in ['bytes', 'sha256']}
    assert pin(ROOT / 'build/cli/omicsmatic-0.3.7.jar') == proof['canonical_executable_unchanged']
    assert all(proof[k] is False for k in ['native_EM_fit_available', 'motif_significance_recomputed', 'full_command_bound',
               'full_report_family_admitted', 'public_discovery_execution_adapter_available', 'catalog_implementation_credit', 'released', 'pushed', 'deployed', 'whole_goal_complete'])
    print(json.dumps(dict(status='verified-native-MEME-discovery-site-matrix-score-and-diagram-evidence', sources=len(proof['source_pins']),
          logical_files=len(files), archive=proof['archive'], tests_per_JVM=94, original_families=42, contributing_sites=4136,
          sequence_diagrams=3040, selected_hits=892, null_tail_cells=64284, catalog_credit=False)))


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable complete evidence validation')
    parser = argparse.ArgumentParser(description=__doc__); parser.add_argument('--freeze', action='store_true'); args = parser.parse_args()
    if args.freeze:
        freeze()
    verify()
