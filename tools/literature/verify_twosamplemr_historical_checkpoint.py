"""Check retained original historical workflow execution evidence; run no analyses."""
from pathlib import Path, PurePosixPath
import argparse, hashlib, json, sys, zipfile


def require(value, message):
    if not value:
        raise ValueError(message)


def pin(raw):
    return dict(bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest())


def safe(name):
    p = PurePosixPath(name)
    require(not p.is_absolute() and '..' not in p.parts and '\\' not in name, 'Unsafe checkpoint path')
    return p.parts


def verify(root):
    q = root / 'docs/literature/twosamplemr-historical-checkpoint'
    p = json.loads((q / 'PROFILE.json').read_bytes())
    require(p['status'] == 'QUALIFIED_HISTORICAL_PREPARED_WORKFLOWS_CATALOG_UNFINISHED'
            and p['new_catalog_credit'] == 0 and not p['whole_goal_complete']
            and p['scientific_sources_unchanged'] and not p['historical_clumping_qualified'], 'Changed qualification scope')
    require(len(p['canonical_unchanged']) == 27, 'Different canonical population')
    for name, wanted in {**p['source_inputs'], **p['canonical_unchanged']}.items():
        require(pin(root.joinpath(*safe(name)).read_bytes()) == wanted, 'Changed source/catalog: ' + name)
    for name, wanted in p['packet_files'].items():
        require(pin(q.joinpath(*safe(name)).read_bytes()) == wanted, 'Changed packet: ' + name)
    inventory = json.loads((q / 'INVENTORY.json').read_bytes())
    unique = {v['sha256'] for v in inventory.values()}
    require(len(inventory) == p['logical_files'] and len(unique) == p['unique_blobs'], 'Changed evidence population')
    with zipfile.ZipFile(q / 'evidence.zip') as z:
        names = z.namelist()
        require(len(names) == len(set(names)) == len(unique) and set(names) == {'blobs/' + s for s in unique}, 'Changed ZIP population')
        total = 0
        for sha in unique:
            require(len(sha) == 64 and all(c in '0123456789abcdef' for c in sha), 'Malformed SHA')
            size = z.getinfo('blobs/' + sha).file_size
            total += size
            require(size <= 32 * 1024 * 1024 and total <= 256 * 1024 * 1024, 'Checkpoint capacity exceeded')
            require(hashlib.sha256(z.read('blobs/' + sha)).hexdigest() == sha, 'Changed evidence blob')
        for name, wanted in inventory.items():
            safe(name)
            require(len(z.read('blobs/' + wanted['sha256'])) == wanted['bytes'], 'Changed byte count')
        def read(name):
            return json.loads(z.read('blobs/' + inventory[name]['sha256']))
        normal = read('current/normal-analyses-1/RESULT.json')
        require(normal['status'] == 'PASS_49_NORMAL_PRODUCER_EXPECTATIONS' and normal['normal_JAR']
                and len(normal['results']) == 49 and normal['successes'] == 45 and normal['original_failures'] == 4
                and all(v['normal_expectation_passed'] and v['exit_code'] == v['expected_exit']
                        and (v['verify_exit'] == 0 if v['exit_code'] == 0 else not v['results_admitted'])
                        for v in normal['results'].values()), 'Different normal outcomes')
        direct = read('current/direct-original-1/RESULT.json')
        require(direct['status'] == 'PASS_FRESH_TAGGED_ORIGINAL_COMPARISONS' and direct['cases'] == 49
                and direct['full_original_returns'] == 121 and direct['original_errors'] == 4
                and set(direct['results']) == set(normal['results'])
                and all(v['exit_code'] == 0 and v['guarded_functions'] == (146 if k.startswith('0.4.9/') else 149)
                        for k, v in direct['results'].items()), 'Different fresh direct-original evidence')
        controls = read('current/cli-controls-1/RESULT.json')
        require(len(controls['results']) == 16 and all(v['exit_code'] == v['expected_exit']
                and not v['results_admitted'] for v in controls['results'].values()), 'Failed control admitted results')
        require(controls['results']['source-mismatch']['no_original_model_invoked'], 'Source mutation reached model')
        cleanup = controls['results']['owned-timeout']
        require(cleanup['owned_child_terminal'] and len(cleanup['owned_cleanup']) >= 2
                and all(not v['alive_after_cleanup'] for v in cleanup['owned_cleanup']), 'Owned worker remained live')
        replay = read('current/replays-examples-1/RESULT.json')
        require(len(replay['results']) == 8 and replay['full_original_returns'] == 26
                and all(v['exit_code'] == 0 and v['RNG_classes_attributes_identical'] for v in replay['results'].values()), 'Replay/example failure')
        for name, result in replay['results'].items():
            if name.startswith('replay-'):
                require(result['parent_files_unchanged'] > 0 and result['exports_identical'], 'Replay changed parent/exports')
            else:
                require(result['only_runtime_paths_substituted'], 'Example changed scientific inputs')
                recipe = name[len('example-'):]
                actual = read('current/replays-examples-1/' + name + '/recipes/' + recipe + '.json')
                expected = json.loads((root / 'examples/twosamplemr-historical/recipes' / (recipe + '.json')).read_bytes())
                ignore = {'rscript', 'r_libraries'}
                require({k: v for k, v in actual.items() if k not in ignore}
                        == {k: v for k, v in expected.items() if k not in ignore}, 'Example request differs')
        require(len(replay['Linux_R_free_verifications']) == 7 and all(v['exit_code'] == 0 and not v['R_executed']
                for v in replay['Linux_R_free_verifications'].values()), 'Different Linux verification scope')
        protection = read('current/RUNTIME-PROTECTION.json')
        require({k: v['files'] for k, v in protection['roots'].items()}
                == {'historical-source': 160, 'historical-runtime': 5382, 'author-fulltext': 3,
                    'protected-predecessor-runtime': 5765}
                and all(v['unchanged'] for v in protection['roots'].values()), 'Private runtime/source changed')
        require(read('preceding-build/COMPLETED.json')['exit_code'] == 0, 'Preceding build failed')
        packaged = read('preceding-build/packaged-RESULT.json')
        require(set(packaged['hosts']) == {'windows21', 'windows25', 'linux21'}
                and all(v['exit_code'] == 0 and v['tests'] == 769 and v['skipped'] == 0
                        for v in packaged['hosts'].values()), 'Different matching packaged tests')
        require(read('preceding-build/JAR-COMPARISON.json')['published_predecessor_payloads_unchanged'] == 22335, 'Predecessor changed')
    return dict(status=p['status'], normal_cases=49, completed=45, expected_original_errors=4,
                full_original_returns_RNG_identical=147, real_replays=4, public_examples=4,
                missing_failed_lifetime_controls=16, Linux_R_free_verifications=7,
                matching_preceding_packaged_tests_each_JVM=769, source_changes=False,
                new_catalog_credit=0, whole_goal_complete=False, fresh_Java_R_execution=False)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args()
    require(__debug__ and sys.version_info >= (3, 11), 'Use Python3.11+ without -O')
    result = verify(Path(__file__).resolve().parents[2])
    args.out.mkdir(parents=True, exist_ok=False)
    (args.out / 'RESULT.json').write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8', newline='\n')
    print(json.dumps(result))


if __name__ == '__main__':
    main()
