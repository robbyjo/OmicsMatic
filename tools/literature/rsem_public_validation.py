"""Current public RSEM workflow evidence, with explicit historical source boundaries."""
import hashlib
import json
from pathlib import PurePosixPath
import zipfile
from rsem_build_evidence import LOCK, open_evidence
from rsem_typed_evidence import verify_typed_fitting
from catalog_source_integrity import verify_source

def verify_public_workflow(root, data, proof):
    assert proof['status'] == 'passed-public-RSEM-source-setup-input-fitting-and-completion-workflows'
    assert proof['public_fitting_workflow_available'] is True and not proof['whole_goal_complete']
    assert proof['source_to_build_provenance_verified'] is True and proof['reusable_runtime_admission_ticket'] is False
    assert proof['tests_per_JVM'] == 244 and proof['existing_native_science_tests_per_JVM'] == 111
    for row in proof['source_evidence']:
        verify_source(root, data, row['path'], row['sha256'])
    typed = json.loads((root / 'docs/literature/RSEM_TYPED_FITTING_VALIDATION.json').read_bytes())
    verify_typed_fitting(root, typed)
    bundle = proof['control_archive']
    old = proof['historical_source_build_control_archive']
    with open_evidence(root / old['path'], old['sha256']) as (old_index, old_read), open_evidence(root / bundle['path'], bundle['sha256']) as (index, read), zipfile.ZipFile(root / typed['control_archive']['path']) as typed_zip:
        typed_index = json.loads(typed_zip.read('index.json'))['logical_files']
        def typed_obj(name):
            return json.loads(typed_zip.read('objects/' + typed_index[name]['sha256']))
        def obj(name):
            return json.loads(read(name))
        logical, private = index['logical_files'], index['private_artifacts']
        assert index['candidate_sha256'] == proof['candidate_sha256']
        assert len(logical) == bundle['logical_files'] and len(private) == bundle['private_artifacts']
        native = obj('native/observed.json')
        assert native['status'] == 'passed244exact-package-tests-three-JVMs-and-strict-Java17-javadoc'
        assert native['candidate_sha256'] == proof['candidate_sha256'] and native['existing_sequence_science_tests_per_JVM'] == 111
        assert native['profiles'] == proof['profiles']
        assert native['profiles']['Linux21'] == dict(found=244, passed=244, skipped=0)
        for label in ['Windows21', 'Windows25']:
            assert native['profiles'][label] == dict(found=244, passed=225, skipped=19)
        assert all(run['exit_code'] == 0 for run in native['runs'])
        assert native['payload_delta']['changed_existing_members'] == ['org/jlinalg/cli/JLinAlgCli.class', 'org/jlinalg/cli/RsemFit.class']
        reconciled = obj('matrix/reconciled.json')
        assert reconciled['status'] == 'passed-public-workflows-and-corrected-cache-precondition-controls'
        assert reconciled['candidate_sha256'] == proof['candidate_sha256'] and not reconciled['production_code_changed_for_recheck']
        current_sources = {row['path']: row['sha256'] for row in proof['source_evidence']}
        for name, pin in native['source_sha256'].items():
            assert current_sources[name] == pin
        for row in index['source_evidence']:
            assert logical['source/' + row['path']]['sha256'] == row['sha256']
            if not row['path'].endswith('.md'):
                assert current_sources[row['path']] == row['sha256']
        source_profiles = {r['version']: r for r in json.loads((root / 'src/main/resources/org/jlinalg/omics/rsem-source-profiles.json').read_bytes())['profiles']}
        new_build = obj('setup/build/build.json')
        assert new_build['status'] == 'COMPLETED_EXACT_ORIGINAL_SOURCE_BUILD' and new_build['source_profile_lock_sha256'] == LOCK
        assert new_build['profile']['version'] == '1.3.3' and new_build['profile']['archiveSha256'] == source_profiles['1.3.3']['source_archive_sha256']
        assert new_build['compiler_prefix'] == new_build['compiler_prefix_after']
        for path, pin in new_build['evidence'].items():
            key = 'setup/build/' + path
            assert (private if key in private else logical)[key]['sha256'] == pin
        for name in ['compiler-prefix-before.jsonl', 'compiler-prefix-after.jsonl']:
            assert logical['setup/build/metadata/' + name]['sha256'] == new_build['compiler_prefix']['sha256']
        assert logical['setup/build/metadata/built-source.jsonl']['sha256'] == new_build['source_graph']['sha256']
        setup_marker = obj('setup/COMPLETED.json')
        new_receipt = logical['setup/build/build.json']['sha256']
        assert setup_marker['build_receipt_sha256'] == new_receipt and setup_marker['scientific_result_admitted'] is False
        assert obj('setup/analysis-status.json')['status'] == 'COMPLETED'
        for phase in new_build['phases']:
            assert phase['exit'] == 0 and phase['owned_live_after_cleanup'] == []
            relative = PurePosixPath(phase['ledger']).relative_to(PurePosixPath(setup_marker['build_home'])).as_posix()
            ledger = obj('setup/build/' + relative)
            assert ledger['status'] == 'completed-process-only' and ledger['exit_code'] == 0 and ledger['owned_live_after_cleanup'] == []
        def graph(raw):
            return {row['path']: row for row in map(json.loads, raw.splitlines()) if row.get('kind') == 'FILE'}
        old_graphs = {v: graph(old_read('build/' + v + '/metadata/built-source.jsonl')) for v in source_profiles}
        new_graph = graph(read('setup/build/metadata/built-source.jsonl'))
        def completed_run(prefix, version, expected, receipt, compiled):
            marker, status = obj(prefix + 'COMPLETED.json'), obj(prefix + 'analysis-status.json')
            result = obj(prefix + 'fit/RESULT_ADMITTED.json')
            assert marker['status'] == 'COMPLETED_ORIGINAL_FITTING_AND_NATIVE_AUDITS' and marker['scientific_result_admitted'] is True
            assert status['status'] == 'COMPLETED' and status['exit_code'] == 0
            assert marker['result_sha256'] == logical[prefix + 'fit/RESULT_ADMITTED.json']['sha256']
            assert result['status'] == 'COMPLETED_ORIGINAL_FIT_AND_NATIVE_AUDITS' and result['fitted_result_admitted'] is True
            assert result['process_exit'] == 0 and result['owned_live_after_cleanup'] == []
            assert result['source_version'] == version and result['source_build_receipt_sha256'] == receipt
            assert result['public_workflow_certification_is_separate'] is True
            actual = {n.removeprefix(prefix + 'fit/'): pin['sha256'] for n, pin in logical.items() if n.startswith(prefix + 'fit/sample')}
            assert actual == expected == result['complete_scientific_output_sha256']
            assert prefix + 'WARNINGS.md' in logical and prefix + 'input-audit/INPUT_AUDIT.json' in logical
            binding, payload = obj(prefix + 'binding/runtime-binding.json'), obj(prefix + 'payload/payload.json')
            assert binding['source_build_receipt_sha256'] == payload['source_build_receipt_sha256'] == receipt
            assert result['runtime_binding_sha256'] == logical[prefix + 'binding/runtime-binding.json']['sha256']
            assert len(payload['files']) == 14
            for name, pin in payload['files'].items():
                key = prefix + 'payload/' + name
                assert (private if key in private else logical)[key]['sha256'] == pin['sha256']
                build_home = PurePosixPath(payload['original_build_home']).parent
                source = str(build_home / 'outputs/omicsmatic-rsem-decode') if name == 'bin/omicsmatic-rsem-decode' else payload['original_build_home'] + '/' + name.removeprefix('bin/')
                assert compiled[source]['sha256'] == pin['sha256'] and compiled[source]['bytes'] == pin['bytes']
            return len(actual), result['numeric_cells']
        completed = scientific = numeric = negatives = 0
        for version in ['1.2.28', '1.3.0', '1.3.1', '1.3.2', '1.3.3']:
            profile = obj('profiles/' + version + '/observed.json')
            assert profile['candidate_sha256'] == proof['candidate_sha256'] and profile['actual_public_main_command_exercised'] is True
            originals = {c['case']: c for c in typed_obj('profiles/' + version + '/observed.json')['completed']}
            expected_count = 6 if version == '1.3.3' else 2 if version == '1.2.28' else 1
            assert len(profile['completed']) == expected_count
            receipt = old_index['logical_files']['build/' + version + '/build.json']['sha256']
            for case in profile['completed']:
                prefix = 'workflow/' + version + '/' + case['case'] + '/run/'
                assert logical[prefix + 'COMPLETED.json']['sha256'] == case['completion_sha256']
                assert case['scientific_sha256'] == originals[case['case']]['scientific_sha256']
                count, cells = completed_run(prefix, version, case['scientific_sha256'], receipt, old_graphs[version])
                assert count == case['scientific_files'] and cells == case['numeric_cells']
                scientific += count; numeric += cells; completed += 1
            assert len(profile['negative']) == 2
            for case in profile['negative']:
                prefix = 'runtime-negative/' + version + '/' + case['case'] + '/run/'
                status = obj(prefix + 'analysis-status.json')
                assert status['exit_code'] == case['exit_code'] == (3 if case['case'] == 'missing-build' else 2)
                assert prefix + 'COMPLETED.json' not in logical and not any(n.startswith(prefix + 'fit/') for n in logical)
                assert logical[prefix + 'WARNINGS.md']['sha256'] == case['warning_sha256']
                negatives += 1
        assert (completed, scientific, numeric, negatives) == (11, 177, 722, 10)
        example_expected = next(c['scientific_sha256'] for c in typed_obj('profiles/1.3.3/observed.json')['completed'] if c['case'] == 'paired-sam')
        example_files, example_cells = completed_run('example/run/', '1.3.3', example_expected, new_receipt, new_graph)
        assert (example_files, example_cells) == (16, 56)
        for case in reconciled['cache_controls']:
            prefix = 'cache-controls/' + case['case'] + '/'
            status = obj(prefix + 'analysis-status.json')
            expected = 3 if case['case'] == 'existing-empty-cache' else 2
            assert status['exit_code'] == case['exit_code'] == expected
            assert status['status'] == ('SKIPPED' if expected == 3 else 'FAILED')
            assert prefix + 'COMPLETED.json' not in logical and not any(n.startswith(prefix + 'build/') for n in logical)
            assert logical[prefix + 'WARNINGS.md']['sha256'] == case['warning_sha256']
        assert proof['public_fitting_cases'] == 12 and proof['complete_public_scientific_files'] == 193 and proof['public_numeric_cells'] == 778
    return dict(public_fitting_cases=12, complete_public_scientific_files=193, public_numeric_cells=778, public_source_builds=1, runtime_negative_controls=10, cache_controls=2, source_to_payload_links_verified=True)
