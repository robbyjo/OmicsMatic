#!/usr/bin/env python3
"""Verify saved mtCOJO execution qualification evidence without executing a model."""
from pathlib import Path, PurePosixPath
import argparse
import hashlib
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
    parser.add_argument('--jar', type=Path, help='Optional exact saved producer')
    args = parser.parse_args()
    out = args.out.resolve()
    out.mkdir(parents=True, exist_ok=False)
    root = Path(__file__).resolve().parents[3]
    q = root / 'docs/literature/mtcojo-qualification'
    try:
        profile = json.loads((q / 'PROFILE.json').read_bytes())
        for name, expected in profile['source_inputs'].items():
            path = PurePosixPath(name)
            assert not path.is_absolute() and '..' not in path.parts
            assert pin(root / name) == expected, 'Source differs: use exact qualification owner: ' + name
        assert pin(q / 'evidence.zip') == profile['archive']
        assert pin(q / 'INVENTORY.json') == profile['inventory']
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
                raw = archive.read('blobs/' + expected['sha256'])
                assert len(raw) == expected['bytes']
                if args.restore:
                    target = out / 'evidence' / name
                    target.parent.mkdir(parents=True, exist_ok=True)
                    with target.open('xb') as stream:
                        stream.write(raw)

            def raw(name):
                return archive.read('blobs/' + inventory[name]['sha256'])

            def read(name):
                return json.loads(raw(name))

            def table(name):
                return [line.split() for line in raw(name).decode('utf-8').splitlines()]

            evidence = read('current/execution-paths-1/COMPLETED.json')
            audit = read('current/execution-audit-1/RESULT.json')
            assert audit['status'] == 'PASS_57_ACTUAL_MTCOJO_EXECUTION_PATHS'
            assert audit['source_or_adapter_changed'] is False
            assert len(audit['initial_harness_substring_corrections']) == 6
            assert all(p['observed_terminal'] for p in audit['terminal_probes'])
            comparisons = 0
            for host, rows in evidence['results'].items():
                assert len(rows) == 19 and audit['per_JVM'][host] == dict(completed=6, FAIL2=10, SKIP3=3)
                for row in rows:
                    assert row['exit_code'] == row['expected_exit']
                    prefix = 'current/execution-paths-1/' + host + '/' + row['name']
                    if row['expected_exit'] == 0:
                        assert row['verify_exit'] == 0 and row['all_original_table_tokens_equal']
                        current = table(prefix + '-bundle/worker/result.mtcojo.cma')
                        original = table('original-references/' + host + '/' + row['name'] + '.mtcojo.cma')
                        assert len(current) == 10001 and current == original
                        comparisons += 1
                    elif row['bundle_created']:
                        assert row['incomplete_verify_exit'] == 2
                        state = read(prefix + '-bundle/analysis-status.json')
                        assert state['results_admitted'] is False
                        assert state['status'] == ('SKIP3_DEPENDENT_ANALYSIS_SKIPPED' if row['expected_exit'] == 3 else 'FAIL2_NO_COMPLETE_RESULT')
                    if row['name'] in ['single-GSMR-low-IV', 'multi-GSMR-low-IV']:
                        assert row['original_exit_code'] == 0 and row['source_NaN_table_retained']
            assert comparisons == 18
            replay = read('current/replay-controls-1/COMPLETED.json')
            assert replay['all_parent_file_hashes_unchanged'] and replay['parent_captures_unchanged'] == 4
            for host, rows in replay['results'].items():
                assert len(rows) == 9
                for row in rows:
                    assert row['exit_code'] == row['expected_exit'] and row['cause_seen']
                    if row['exit_code'] == 0:
                        assert row['verify_exit'] == 0 and row['matches_target_host_original_tokens'] and row['all_captured_input_bytes_preserved']
                        current = table('current/replay-controls-1/' + host + '/' + row['name'] + '-replayed/worker/result.mtcojo.cma')
                        expected = table('current/execution-paths-1/' + host + '/' + row['name'] + '-bundle/worker/result.mtcojo.cma')
                        assert current == expected
                        comparisons += 1
            examples = read('current/examples-1/COMPLETED.json')
            assert examples['status'] == 'PASS_NATIVE_AND_ORIGINAL_EXAMPLES'
            assert len(examples['native_results']) == 3 and len(examples['original_results']) == 2
            assert all(r['exit_code'] == 0 and r['independent_fields'] == 12 for r in examples['native_results'].values())
            assert all(r['exit_code'] == r['verify_exit'] == 0 and r['all_original_table_tokens_equal'] and r['existing_request_preserved'] for r in examples['original_results'].values())
            for host in examples['original_results']:
                expected_host = 'linux21' if host == 'linux21' else 'windows21'
                assert table('current/examples-1/' + host + '/original-example-new/worker/result.mtcojo.cma') == table('current/execution-paths-1/' + expected_host + '/multi-supplied-gzip-bundle/worker/result.mtcojo.cma')
                comparisons += 1
            seals = [name for name in inventory if name.endswith('/capture-files.json')]
            for name in seals:
                prefix = name[:-len('capture-files.json')]
                expected = read(name)['files']
                actual = {n[len(prefix):]: v['sha256'] for n, v in inventory.items() if n.startswith(prefix) and n != name}
                assert actual == expected, 'Saved capture population/hash mismatch: ' + prefix
            assert len(seals) == 59 and comparisons == 24
        if args.jar:
            assert pin(args.jar) == profile['normal_producer'], 'Exact saved producer mismatch'
        result = dict(status='PASS_SAVED_MTCOJO_EXECUTION_QUALIFICATION_BYTES_ONLY', logical_files=len(inventory),
                      unique_blobs=len(unique), execution_observations=57, replay_observations=18,
                      original_table_comparisons=24, exact_capture_seals_checked=len(seals),
                      saved_zero_exit_NaN_GSMR_refusals=6, saved_owned_PID_terminal_probes=len(audit['terminal_probes']),
                      native_example_hosts=3, original_example_hosts=2, model_executed=False,
                      normal_producer_bytes_checked=bool(args.jar), new_catalog_credit=0, whole_goal_complete=False)
        (out / 'RESULT.json').write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8', newline='\n')
        print(json.dumps(result))
    except BaseException as error:
        (out / 'FAILURE.txt').write_text(repr(error) + '\n', encoding='utf-8', newline='\n')
        raise


if __name__ == '__main__':
    main()
