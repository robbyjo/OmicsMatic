"""Verify complete retained typed/direct RSEM scientific controls and frozen source epochs."""
from pathlib import Path
import hashlib
import json
import re
import zipfile

def verify_typed_fitting(root, record):
    root = Path(root)
    bundle = record['control_archive']
    archive = root / bundle['path']
    assert hashlib.sha256(archive.read_bytes()).hexdigest() == bundle['sha256']
    assert archive.stat().st_size == bundle['bytes'] < 100_000_000
    with zipfile.ZipFile(archive) as z:
        entries = z.infolist()
        assert len(entries) == len({e.filename for e in entries}) < 50001
        assert z.getinfo('index.json').file_size <= 16_777_216
        index = json.loads(z.read('index.json'))
        files = index['logical_files']
        assert len(files) == index['logical_file_count'] == bundle['logical_files']
        objects = {row['sha256'] for row in files.values()}
        assert set(z.namelist()) == {'index.json'} | {'objects/' + pin for pin in objects}
        assert len(objects) == index['object_count'] == bundle['objects']
        cache = {}
        for pin in objects:
            assert re.fullmatch('[0-9a-f]{64}', pin)
            assert z.getinfo('objects/' + pin).file_size <= 67_108_864
            raw = z.read('objects/' + pin)
            assert hashlib.sha256(raw).hexdigest() == pin
            cache[pin] = raw
        decoded = 0
        for name, row in files.items():
            assert not name.startswith('/') and '\\' not in name and all(p not in ['', '.', '..'] for p in name.split('/'))
            assert len(cache[row['sha256']]) == row['bytes']
            decoded += row['bytes']
        assert decoded == index['decoded_bytes'] == bundle['decoded_bytes'] <= 1_073_741_824
        def raw(name):
            return cache[files[name]['sha256']]
        def obj(name):
            return json.loads(raw(name))
        source = {row['path']: row for row in index['source_evidence']}
        assert len(source) == len(index['source_evidence']) == bundle['source_files']
        for name, row in source.items():
            assert row['archive_path'] == 'source/' + name
            assert files[row['archive_path']]['sha256'] == row['sha256']
        for row in record['source_evidence']:
            assert files['source/' + row['path']]['sha256'] == row['sha256'] == source[row['path']]['sha256']
        matrix = obj('matrix/observed.json')
        assert matrix['status'] == 'passed-fresh-typed-versus-independent-original-fitting-matrix'
        assert matrix['candidate_sha256'] == record['candidate_sha256']
        assert set(matrix['versions']) == {'1.2.28', '1.3.0', '1.3.1', '1.3.2', '1.3.3'}
        original_bundle = root / record['source_build_control_archive']['path']
        assert hashlib.sha256(original_bundle.read_bytes()).hexdigest() == record['source_build_control_archive']['sha256']
        with zipfile.ZipFile(original_bundle) as original:
            build_index = json.loads(original.read('index.json'))['logical_files']
        positives = failures = scientific = numeric = 0
        for version in matrix['versions']:
            profile = obj('profiles/' + version + '/observed.json')
            receipt = build_index['build/' + version + '/build.json']['sha256']
            assert profile['source_build_receipt_sha256'] == receipt
            assert profile['candidate_sha256'] == record['candidate_sha256']
            expected_failures = 2 if version in {'1.2.28', '1.3.0', '1.3.1'} else 0
            assert len(profile['completed']) == 15 - expected_failures and len(profile['historical_failures']) == expected_failures
            for case in profile['completed']:
                prefix = 'positive/' + version + '/' + case['case'] + '/'
                assert files[prefix + 'audit/INPUT_AUDIT.json']['sha256'] == case['input_audit_sha256']
                assert files[prefix + 'typed/RESULT_ADMITTED.json']['sha256'] == case['typed_completion_sha256']
                result = obj(prefix + 'typed/RESULT_ADMITTED.json')
                assert result['status'] == 'COMPLETED_PRIVATE_TYPED_ORIGINAL_FIT_AND_NATIVE_AUDITS'
                assert result['fitted_result_admitted'] is True and result['process_exit'] == 0 and result['owned_live_after_cleanup'] == []
                assert result['source_build_receipt_sha256'] == receipt and result['source_version'] == version
                assert result['complete_scientific_output_sha256'] == case['scientific_sha256']
                for kind in ['typed', 'direct']:
                    start = prefix + kind + '/'
                    actual = {name[len(start):]: row['sha256'] for name, row in files.items() if name.startswith(start + 'sample')}
                    assert actual == case['scientific_sha256']
                    scientific += len(actual)
                assert result['transcripts'] == case['transcripts'] and result['genes'] == case['genes']
                assert result['numeric_cells'] == case['numeric_cells']
                numeric += case['numeric_cells']; positives += 1
            for case in profile['historical_failures']:
                prefix = 'historical-failure/' + version + '/' + case['case'] + '/'
                assert prefix + 'typed/RESULT_ADMITTED.json' not in files and prefix + 'typed/FIT_FAILED.txt' in files
                assert files[prefix + 'typed/process.json']['sha256'] == case['typed_process_ledger_sha256']
                assert files[prefix + 'direct/process.json']['sha256'] == case['direct_process_ledger_sha256']
                assert case['result_admitted'] is False
                failures += 1
        assert (positives, failures, scientific, numeric) == (69, 6, 2136, 4624)
        assert matrix['unexpected_reference_requests'] == 0 and matrix['changed_result_negative_controls'] == 5
        assert record['matched_completed_cases'] == positives and record['matched_historical_failure_cases'] == failures
        assert record['scientific_files_both_sides'] == scientific and record['numeric_cells'] == numeric
    return dict(status='passed-complete-typed-original-scientific-and-source-evidence', positive_cases=positives, historical_failure_cases=failures, scientific_files_both_sides=scientific, numeric_cells=numeric, source_files=len(source))
