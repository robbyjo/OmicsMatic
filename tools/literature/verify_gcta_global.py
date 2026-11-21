#!/usr/bin/env python3
"""Read saved GCTA global-command/source-role evidence; never execute Java or models."""
from pathlib import Path, PurePosixPath
import argparse
import hashlib
import importlib.util
import json
import re
import zipfile


def pin(path):
    with path.open('rb') as stream:
        return dict(bytes=path.stat().st_size, sha256=hashlib.file_digest(stream, 'sha256').hexdigest())


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, required=True, help='NEW receipt directory')
    parser.add_argument('--restore', action='store_true')
    parser.add_argument('--jar', type=Path, help='Optional exact saved normal producer')
    args = parser.parse_args()
    out = args.out.resolve()
    out.mkdir(parents=True, exist_ok=False)
    root = Path(__file__).resolve().parents[2]
    q = root / 'docs/literature/gcta-global-development'
    try:
        profile = json.loads((q / 'PROFILE.json').read_bytes())
        for name, expected in profile['source_inputs'].items():
            path = PurePosixPath(name)
            assert not path.is_absolute() and '..' not in path.parts
            assert pin(root / name) == expected, 'Source differs: use the exact global owner: ' + name
        assert pin(q / 'evidence.zip') == profile['archive'] and pin(q / 'INVENTORY.json') == profile['inventory']
        assert pin(q / 'SOURCE-ROLES.json') == profile['source_roles']
        assert pin(q / 'SOURCE-CENSUS.json') == profile['source_census']
        inventory = json.loads((q / 'INVENTORY.json').read_bytes())
        unique = {v['sha256'] for v in inventory.values()}
        with zipfile.ZipFile(q / 'evidence.zip') as archive:
            assert len(archive.namelist()) == len(set(archive.namelist())) == len(unique)
            assert set(archive.namelist()) == {'blobs/' + sha for sha in unique}
            for sha in unique:
                assert re.fullmatch('[0-9a-f]{64}', sha)
                assert hashlib.sha256(archive.read('blobs/' + sha)).hexdigest() == sha
            for name, expected in inventory.items():
                path = PurePosixPath(name)
                assert not path.is_absolute() and '..' not in path.parts
                data = archive.read('blobs/' + expected['sha256'])
                assert len(data) == expected['bytes']
                if args.restore:
                    target = out / 'evidence' / name
                    target.parent.mkdir(parents=True, exist_ok=True)
                    with target.open('xb') as stream:
                        stream.write(data)

            def raw(name):
                return archive.read('blobs/' + inventory[name]['sha256'])

            def read(name):
                return json.loads(raw(name))

            def load(name, path):
                spec = importlib.util.spec_from_file_location(name, path)
                module = importlib.util.module_from_spec(spec)
                spec.loader.exec_module(module)
                return module

            transition = load('gcta_saved_transition', root / 'tools/literature/gcta_dispatch_transition.py')
            before = transition.dispatcher_before((root / transition.DISPATCH).read_bytes())
            assert before == raw('current/dispatcher-edit-1/before.java')
            old = load('gcta_preceding_GSEA_transition', root / 'tools/literature/gsea_dispatch_transition.py')
            assert old.pin(old.dispatcher_before(before)) == old.BEFORE
            controls = read('current/transition-controls-1/RESULT.json')
            assert len(controls['controls']) == 7 and all(r['status'] == 'EXPECTED_REFUSAL' for r in controls['controls'])
            assert controls['predecessor_exit'] == 0 and controls['predecessor_worktree_unchanged']
            assert not controls['source_overlay_used'] and not controls['old_hashes_refreshed']
            assert controls['predecessor_completed']['current_manifest']['entries'] == 826
            build = read('current/normal-gradle-1/COMPLETED.json')
            assert build['exit_code'] == 0 and sum(r['tests'] for r in build['reports'].values()) == 27
            assert all(r['failures'] == r['errors'] == r['skipped'] == 0 for r in build['reports'].values())
            packaged = read('current/packaged-global-1/COMPLETED.json')
            assert packaged['producer'] == profile['normal_producer']
            assert packaged['payloads'] == dict(unchanged_payloads=23547, changed=['org/jlinalg/cli/JLinAlgCli.class'], added=[], removed=[])
            assert set(packaged['results']) == {'windows21', 'windows25', 'linux21'}
            for host in packaged['results'].values():
                assert host['tests'] == 27 and host['test_exit'] == 0 and len(host['global_capture_verifications']) == 82
                for row in host['global_capture_verifications']:
                    assert row['exit_code'] == 0 and row['command'][-5] == '-jar' and row['command'][-3:-1] == ['gcta-tools', '--verify']
            workflows = read('current/global-workflows-2/COMPLETED.json')
            assert workflows['producer'] == profile['normal_producer']
            table_checks = 0
            for host, result in workflows['results'].items():
                assert len(result['invocations']) == 25 and all(r['exit_code'] == r['expected_exit'] for r in result['invocations'])
                assert result['legacy_command_help_bytes_unchanged'] == 4 and result['root_help_exact_except_one_new_line']
                assert result['native_RUN_VERIFY_REPLAY'] and result['original_RUN_VERIFY_REPLAY'] and result['parent_bytes_preserved']
                assert result['zero_exit_NaN_source_refused'] and result['missing_runtime_SKIP3_without_fallback']
                assert len(result['table_comparisons']) == 4 and all(r['all_original_table_tokens_equal'] for r in result['table_comparisons'])
                table_checks += 4
                for command in ['broad-gsea-tools', 'edger-tools', 'susie-tools', 'scanpy-tools']:
                    for channel in ['stdout', 'stderr']:
                        assert raw('current/global-workflows-2/' + host + '/old-' + command + '.' + channel) == raw('current/global-workflows-2/' + host + '/current-' + command + '.' + channel)
            roles = json.loads((q / 'SOURCE-ROLES.json').read_bytes())
            assert len(roles['roles']) == 27 and len({r['paper_id'] for r in roles['roles']}) == 19 and len(roles['exclusions']) == 4
            assert roles['expected_observation_delta'] == 1 and not roles['canonical_metadata_modified']
            assert {r['paper_id'] for r in roles['proposed_additions']} == {'P112', 'P169'}
            assert roles['proposed_removal']['paper_id'] == 'P127'
            census = json.loads((q / 'SOURCE-CENSUS.json').read_bytes())
            assert len(census['source_identities']) == 250 and len(census['candidates']) == 23 and census['new_papers'] == ['P169']
            assert census['all_prior_candidate_paragraphs_retained'] and not census['canonical_metadata_modified']
            seals = [n for n in inventory if n.endswith('/capture-files.json')]
            for name in seals:
                prefix = name[:-len('capture-files.json')]
                expected = read(name)['files']
                observed = {n[len(prefix):]: v['sha256'] for n, v in inventory.items() if n.startswith(prefix) and n != name}
                assert observed == expected, 'Capture seal/population differs: ' + prefix
        if args.jar:
            assert pin(args.jar) == profile['normal_producer'], 'Exact saved producer differs'
        result = dict(status='PASS_SAVED_GCTA_GLOBAL_AND_27_REVIEWED_LABEL_ROLES_NOT_CATALOG_ADMISSION',
                      logical_files=len(inventory), unique_blobs=len(unique), saved_tests_per_JVM=27, JVMs=3,
                      saved_global_capture_verifications=246, saved_actual_invocations=75,
                      saved_original_table_comparisons=table_checks, exact_capture_seals_checked=len(seals),
                      reviewed_label_roles=27, executed_papers=19, source_candidates=23, exclusions=4,
                      model_executed=False, normal_producer_bytes_checked=bool(args.jar),
                      new_catalog_credit=0, whole_goal_complete=False)
        (out / 'RESULT.json').write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8', newline='\n')
        print(json.dumps(result))
    except BaseException as error:
        (out / 'FAILURE.txt').write_text(repr(error) + '\n', encoding='utf-8', newline='\n')
        raise


if __name__ == '__main__':
    main()
