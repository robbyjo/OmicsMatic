"""Keep historical original-runtime evidence separate from current source tests."""
import hashlib
import json
import zipfile

from catalog_source_integrity import verify_source


def verify_historical_snapshot(root, runtime):
    assert runtime['current_source_validation'] is False
    snapshot = runtime['historical_source_snapshot']
    archive = root / snapshot['path']
    assert hashlib.sha256(archive.read_bytes()).hexdigest() == snapshot['sha256']
    expected = {row['path']: row['sha256'] for row in runtime['source_evidence']}
    assert len(expected) == len(runtime['source_evidence']) == 57
    with zipfile.ZipFile(archive) as source:
        entries = source.infolist()
        assert len(entries) == len(expected)
        assert {entry.filename for entry in entries} == set(expected)
        assert sum(entry.file_size for entry in entries) <= 67108864
        for entry in entries:
            assert entry.file_size <= 16777216
            assert hashlib.sha256(source.read(entry)).hexdigest() == expected[entry.filename]


def verify_runtime_development(root, data, runtime):
    verify_historical_snapshot(root, runtime)
    current = json.loads((root / 'docs/literature/RSEM_SOURCE_SETUP_VALIDATION.json').read_text(encoding='utf-8'))
    assert current['status'] == 'passed-focused-tests-source-setup-incomplete'
    assert current['tests_per_JVM'] == 166
    assert current['existing_native_science_tests_per_JVM'] == 111
    assert current['source_setup_attempt']['status'] == 'SKIPPED_DEPENDENCY_UNAVAILABLE'
    assert current['source_setup_attempt']['completed_source_build'] is False
    for name in ['source_to_build_provenance_verified', 'scientific_runtime_admitted',
                 'public_fitting_workflow_available', 'whole_goal_complete']:
        assert current[name] is False
    for row in current['source_evidence']:
        verify_source(root, data, row['path'], row['sha256'])
    return current
