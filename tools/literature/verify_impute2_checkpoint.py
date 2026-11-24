"""Verify the scoped IMPUTE2 development checkpoint; no catalog admission or scientific refit."""
from pathlib import Path
import argparse, hashlib, json, zipfile

def require(condition, message):
    if not condition:
        raise ValueError(message)

def pin(raw):
    return dict(bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest())

def file_pin(path):
    digest = hashlib.sha256()
    size = 0
    with path.open('rb') as stream:
        for block in iter(lambda: stream.read(1048576), b''):
            digest.update(block)
            size += len(block)
    return dict(bytes=size, sha256=digest.hexdigest())

def safe(name):
    require(isinstance(name, str) and name and not name.startswith('/') and '\\' not in name and ':' not in name and all(p not in ('', '.', '..') for p in name.split('/')), 'Unsafe evidence path')
    return name

def verify(root, jar=None, private_root=None):
    root = root.resolve()
    folder = root / 'docs/literature/impute2-checkpoint'
    state = json.loads((folder / 'STATUS.json').read_bytes())
    require(state['status'] == 'PASS_SCOPED_IMPUTE2_FOUR_OPERATIONS_CONDITIONAL_BOUNDS' and not state['catalog_admitted'], 'Wrong checkpoint scope')
    require((state['new_family_credit'], state['new_reviews'], state['whole_goal_complete']) == (0, 0, False), 'Expanded admission claim')
    for name, key in [('FILES.json', 'manifest'), ('evidence.zip', 'archive'), ('OMISSIONS.json', 'omissions')]:
        require(file_pin(folder / name) == state[key], 'Changed evidence packet: ' + name)
    manifest = json.loads((folder / 'FILES.json').read_bytes())
    require(len(manifest) == state['logical_files'], 'Changed logical population')
    with zipfile.ZipFile(folder / 'evidence.zip') as archive:
        names = archive.namelist()
        require(len(names) == len(set(names)) == state['unique_objects'] and set(names) == {'objects/' + x['sha256'] for x in manifest.values()}, 'Changed archive population')
        require(sum(x.file_size for x in archive.infolist()) <= 100_000_000, 'Oversized evidence archive')
        objects = {name: archive.read(name) for name in names}
    for name, identity in manifest.items():
        safe(name)
        require(pin(objects['objects/' + identity['sha256']]) == identity, 'Changed logical evidence: ' + name)
    def read(name):
        return objects['objects/' + manifest[name]['sha256']]
    def obj(name):
        return json.loads(read(name))
    for name, identity in state['source_code'].items():
        require(file_pin(root / safe(name)) == pin(read('source/' + name)) == identity, 'Changed source: ' + name)
    normal = obj('normal-build-3/RESULT.json')
    require(normal['exit_code'] == 0 and normal['source_unchanged'] and normal['normal_tests'] == dict(tests=1856, failures=0, errors=0, skipped=0), 'Incomplete normal build/tests')
    require(state['producer'] == {k: normal['producer'][k] for k in ('bytes', 'sha256')}, 'Wrong producer identity')
    payload = obj('normal-build-3/PAYLOADS.json')
    require(payload['modified'] == ['org/jlinalg/cli/JLinAlgCli.class'] and payload['previous_payloads'] == 23068, 'Predecessor payload changed')
    require(payload['added'] and all(n.startswith(('org/jlinalg/cli/Impute', 'org/jlinalg/genetics/RoundedGenotypeProbabilities')) for n in payload['added']), 'Unrelated added class')
    first = obj('workflow-qualification-1/BOUNDS-NOT-ADMITTED.json')
    require(first['status'] == 'ORIGINAL_SCIENTIFIC_BYTE_MATCHES_RETAINED_NEAREST_ONLY_BOUNDS_SUPERSEDED', 'Lost superseded rounding assumption')
    qualification = obj('workflow-qualification-2/RESULT.json')
    require(qualification['status'] == 'PASS_FOUR_ORIGINAL_OPERATIONS_ACTUAL_RUN_VERIFY_REPLAY' and len(qualification['steps']) == 25 and qualification['original_runtime_examples_unchanged'], 'Incomplete original-runtime qualification')
    require(all(s['exit_code'] == s['expected_exit'] == 0 for s in qualification['steps'].values()), 'Failed original-runtime operation')
    require(set(qualification['original_control_matches']) == {'unphased', 'prephase', 'prephased', 'merge'} and sum(map(len, qualification['original_control_matches'].values())) == 13, 'Incomplete full scientific comparisons')
    merged = obj('merged-panel-qualification-1/RESULT.json')
    require(merged['status'] == 'PASS_JAVA_MERGE_OUTPUT_TO_IMPUTATION_ACTUAL_RUN_VERIFY_REPLAY' and set(merged['steps']) == {'RUN', 'VERIFY', 'REPLAY'} and all(s['exit_code'] == 0 for s in merged['steps'].values()) and len(merged['scientific_files_matched']) == 3 and merged['read_only_merge_parent_unchanged'], 'Missing composed panel-merge-to-imputation qualification')
    boundary = obj('boundary-controls-1/RESULT.json')
    require(boundary['status'] == 'PASS_ACTUAL_PACKAGED_BOUNDARY_CONTROLS' and len(boundary['steps']) == 19 and boundary['original_parent_unchanged'] and boundary['captured_inputs_replayed_with_external_paths_absent'], 'Incomplete boundary controls')
    require(all(s['exit_code'] == s['expected_exit'] for s in boundary['steps'].values()) and sum(s['exit_code'] != 0 for s in boundary['steps'].values()) == 12, 'Incorrect refusal population')
    require(not any(s['same_identity_alive'] for s in obj('boundary-controls-1/DEADLINE-OWNERSHIP.json')['owned_processes']), 'Unclosed owned process')
    windows = obj('windows-boundary-1/RESULT.json')
    require(windows['status'] == 'PASS_WINDOWS_HELP_AND_PROMINENT_SKIP3_WITH_WSL_REMEDY' and windows['no_original_process_launched'] and windows['steps']['runtime-skip']['exit_code'] == 3, 'Missing Windows skip/remedy')
    censor = obj('zero-censor-audit-1/RESULT.json')
    require(censor['status'] == 'PASS_EMPIRICAL_SUBUNIT_ZERO_CENSOR_CONTROL', 'Missing original precision control')
    for digits in ('6', '9'):
        counts = censor['comparisons'][digits]['counts']
        require(counts['components'] == 180000 and counts['nearest_zero_rule_falsified'] > 1000 and not any(counts.get(k, 0) for k in ('zero_censor_violation', 'nonzero_nearest_violation', 'nonzero_minimum_violation')), 'Expanded quantizer claim')
    proxy = obj('conditional-bounds-proxy-check-1/RESULT.json')
    require(proxy['status'] == 'PASS_CONDITIONAL_BOUNDS_VS_ORIGINAL_9DP_PROXIES' and proxy['comparisons'] == 180000 and proxy['genotypes'] == 60000 and proxy['no_renormalization'], 'Missing higher-precision conditional proxy check')
    previous = obj('predecessor-readback-2/RESULT.json')
    require(previous['exit_code'] == 0 and not previous['previous_current_source_reader_claimed'] and obj('predecessor-readback-2/SOURCE-EXTENSION.json')['status'] == 'EXACT_THREE_LINE_NEW_COMMAND_EXTENSION', 'Lost predecessor source context or precise extension')
    private_checked = 0
    if jar is not None:
        require(file_pin(jar) == state['producer'], 'Restore the exact complete producer JAR; no historical hash refresh')
    if private_root is not None:
        for name, identity in state['private_evidence'].items():
            require(file_pin(private_root / safe(name)) == identity, 'Changed private evidence: ' + name)
            private_checked += 1
    return dict(status=state['status'], catalog_admitted=False, new_family_credit=0, new_reviews=0, normal_tests=1856, original_operations=4, actual_RUN_VERIFY_REPLAY_fits=15, scientific_files_compared_to_original_controls=16, boundary_refusals=12, source_checked=True, exact_JAR_checked=jar is not None, private_evidence_checked=private_checked, conditional_bounds_not_hidden_posterior_reconstruction=True, quantizer_control_empirical_not_formal_global_proof=True, scientific_analysis_reexecuted=False, execution_authenticated=False, whole_goal_complete=False)

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path, default=Path(__file__).resolve().parents[2])
    parser.add_argument('--jar', type=Path)
    parser.add_argument('--private-root', type=Path)
    args = parser.parse_args()
    try:
        print(json.dumps(verify(args.root, args.jar, args.private_root), indent=2))
    except Exception as error:
        raise SystemExit('FAIL2: IMPUTE2 checkpoint invalid: ' + repr(error) + '. Preserve evidence and restore the exact source, producer or private inputs; never refresh historical hashes.') from error
