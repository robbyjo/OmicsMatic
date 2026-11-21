"""Verify retained global TwoSampleMR qualification without executing scientific tools."""
from pathlib import Path, PurePosixPath
import argparse, hashlib, json, sys, xml.etree.ElementTree as ET, zipfile
from twosamplemr_dispatch_transition import dispatcher_before


def require(ok, message):
    if not ok:
        raise ValueError(message)


def pin(raw):
    return dict(bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest())


def safe(name):
    p = PurePosixPath(name)
    require(not p.is_absolute() and '..' not in p.parts and '\\' not in name, 'Unsafe saved path')
    return p.parts


def verify(root):
    q = root / 'docs/literature/twosamplemr-global-checkpoint'
    profile = json.loads((q / 'PROFILE.json').read_bytes())
    require(profile['status'] == 'PASS_GLOBAL_TWOSAMPLEMR858_CATALOG_UNFINISHED'
            and profile['global_command_registered'] and profile['new_catalog_credit'] == 0
            and not profile['whole_goal_complete'], 'Different qualification scope')
    for name, wanted in {**profile['source_inputs'], **profile['canonical_unchanged']}.items():
        require(pin(root.joinpath(*safe(name)).read_bytes()) == wanted, 'Changed source/catalog: ' + name)
    require(len(profile['canonical_unchanged']) == 27, 'Different canonical population')
    dispatcher_before((root / 'src/main/java/org/jlinalg/cli/JLinAlgCli.java').read_bytes())
    for name, wanted in profile['packet_files'].items():
        require(pin(q.joinpath(*safe(name)).read_bytes()) == wanted, 'Changed packet: ' + name)
    inventory = json.loads((q / 'INVENTORY.json').read_bytes())
    unique = {v['sha256'] for v in inventory.values()}
    require(len(inventory) == profile['logical_files'] and len(unique) == profile['unique_blobs'], 'Changed evidence population')
    with zipfile.ZipFile(q / 'checks.zip') as z:
        names = z.namelist()
        require(len(names) == len(set(names)) == len(unique) and set(names) == {'blobs/' + s for s in unique}, 'Different ZIP population')
        total = 0
        for sha in unique:
            require(len(sha) == 64 and all(c in '0123456789abcdef' for c in sha), 'Invalid blob key')
            size = z.getinfo('blobs/' + sha).file_size
            total += size
            require(size <= 32 * 1024 * 1024 and total <= 256 * 1024 * 1024, 'Checkpoint capacity exceeded')
            require(hashlib.sha256(z.read('blobs/' + sha)).hexdigest() == sha, 'Changed evidence blob')
        for name, wanted in inventory.items():
            safe(name)
            require(len(z.read('blobs/' + wanted['sha256'])) == wanted['bytes'], 'Different byte count')
        def read(name):
            return json.loads(z.read('blobs/' + inventory['current/' + name]['sha256']))
        require(read('normal-gradle-1/COMPLETED.json')['exit_code'] != 0, 'Initial failed build mislabeled')
        normal = read('normal-gradle-2/COMPLETED.json')
        require(normal['exit_code'] == 0 and sum(v['tests'] for v in normal['reports'].values()) == 858
                and all(v[k] == 0 for v in normal['reports'].values() for k in ['failures', 'errors', 'skipped']), 'Normal build/tests failed')
        for name, result in normal['reports'].items():
            raw = z.read('blobs/' + inventory['current/normal-gradle-2/test-results/TEST-' + name + '.xml']['sha256'])
            xml = ET.fromstring(raw)
            require(int(xml.attrib['tests']) == result['tests'] and all(int(xml.attrib[k]) == 0 for k in ['failures', 'errors', 'skipped']), 'Different raw test XML')
        packaged = read('packaged-1/RESULT.json')
        require(set(packaged['hosts']) == {'windows21', 'windows25', 'linux21'}
                and all(v['exit_code'] == 0 and v['tests'] == 858 and v['skipped'] == 0 for v in packaged['hosts'].values()), 'Packaged host failed')
        jar = read('packaged-1/JAR-COMPARISON.json')
        require(jar['preceding_payloads'] == 22375 and jar['preceding_payloads_unchanged'] == 22374
                and jar['changed_preceding_payloads'] == ['org/jlinalg/cli/JLinAlgCli.class']
                and jar['all_original_scientific_classes_resources_unchanged'], 'Changed preceding scientific payload')
        execution = read('global-examples-replays-1/RESULT.json')
        require(execution['global_command_registered'] and len(execution['results']) == 7
                and execution['complete_original_returns'] == 50, 'Different actual global qualification')
        for name, result in execution['results'].items():
            require(set(result) == {'example', 'replay'} and all(v['exit_code'] == 0 and v['RNG_classes_attributes_identical'] for v in result.values()), 'Original comparison failed')
            require(result['example']['only_runtime_paths_substituted'] and result['replay']['exports_identical']
                    and result['replay']['parent_files_unchanged'] > 0, 'Example or replay parent changed')
            for operation in ['run', 'verify', 'replay', 'replay-verify', 'example-compare', 'replay-compare', 'linux-verify']:
                require(read('global-examples-replays-1/' + name + '/' + operation + '-PROCESS.json')['exit_code'] == 0, 'Missing actual process success')
        require(len(execution['Linux_R_free_verifications']) == 7 and all(v['exit_code'] == 0 and not v['R_executed'] for v in execution['Linux_R_free_verifications'].values()), 'Different Linux scope')
        controls = read('cli-controls-1/RESULT.json')
        require(len(controls['results']) == 10 and all(v['exit_code'] == v['expected_exit'] and not v['results_admitted'] and v['prominent_recovery'] for v in controls['results'].values()), 'Failed global refusal/admission')
        require(len(controls['neighbor_help']) == 6 and all(v['exit_code'] == 0 and v['stdout_stderr_identical'] for v in controls['neighbor_help'].values()), 'Changed neighbor help')
        for host in ['windows', 'linux']:
            source = read('source-audit-' + host + '-1/RESULT.json')
            require(source['papers'] == 250 and source['positive_papers'] == 14 and source['semantic_refusals'] == 16, 'Different fresh source audit')
        context = read('peer-owner-context-1/RESULT.json')
        require(context['historical_head'] == 'ac75e6958d7cf4cf6ec6372ec0548e1dcb1dcee1'
                and context['historical_status_unchanged'] and context['fresh_all250_preceding_source_audit']
                and not context['source_overlay_used'] and not context['old_hashes_refreshed']
                and all(v['exit_code'] == 0 for v in context['processes'].values()), 'Changed preceding catalog owner')
        protection = read('RUNTIME-PROTECTION.json')
        require(all(v['unchanged'] for v in protection['roots'].values())
                and protection['roots']['protected-predecessor-runtime']['files'] == 5765
                and protection['roots']['historical-runtime']['files'] == 5382, 'Changed private runtime')
    data = root / 'docs/literature/omics-2023-2026'
    members = {}
    for line in (data / 'SHA256SUMS').read_text().splitlines():
        sha, name = line.split('  ', 1)
        target = (data / name).resolve()
        require(target.is_relative_to(root / 'docs/literature') and name not in members, 'Unsafe/duplicate manifest path')
        with target.open('rb') as stream:
            require(hashlib.file_digest(stream, 'sha256').hexdigest() == sha, 'Changed catalog manifest member')
        members[name] = sha
    require(len(members) == 835, 'Different preceding manifest population')
    return dict(status=profile['status'], packaged_tests_per_JVM=858, JVMs=3, global_profiles=7,
                global_examples=7, actual_global_replays=7, complete_original_returns_classes_RNG_identical=50,
                Linux_R_free_verifications=7, global_refusals=10, neighbor_help_identities=6,
                fresh_primary_papers_per_host=250, source_roles=14, source_refusals_per_host=16,
                source_hosts=2, global_command_registered=True, canonical_files_unchanged=27,
                scoped_integrations=143, reviewed_uses=1616, pending_uses=1104,
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
