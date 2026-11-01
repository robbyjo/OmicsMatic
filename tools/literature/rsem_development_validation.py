"""Keep historical original-runtime evidence separate from current source tests."""
import hashlib
import json
import zipfile

from catalog_source_integrity import verify_source
from rsem_build_evidence import verify_source_build_bundle


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
    assert current['status'] == 'passed-owned-source-builds-and-original-fitting-development'
    assert current['tests_per_JVM'] == 171
    assert current['existing_native_science_tests_per_JVM'] == 111
    assert current['completed_source_builds'] == 5 and current['source_to_build_provenance_verified'] is True
    assert current['completed_original_fits'] == 22 and current['complete_scientific_files'] == 364
    assert current['complete_reference_files'] == 30 and current['negative_dependency_controls'] == 30
    for name in ['scientific_runtime_admitted',
                 'public_fitting_workflow_available', 'whole_goal_complete']:
        assert current[name] is False
    for row in current['source_evidence']:
        verify_source(root, data, row['path'], row['sha256'])
    bundle=current['control_bundle']
    observed=verify_source_build_bundle(root,root/bundle['path'],bundle['sha256'],current['candidate_sha256'])
    assert observed == current['independent_bundle_verification']
    return current
