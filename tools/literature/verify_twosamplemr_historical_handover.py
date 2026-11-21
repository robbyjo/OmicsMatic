"""Verify retained historical TwoSampleMR handover; execute no Java/R analysis."""
from pathlib import Path, PurePosixPath
import argparse, hashlib, json, sys, zipfile


def require(value, message):
    if not value:
        raise ValueError(message)


def pin(raw):
    return dict(bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest())


def safe(name):
    p = PurePosixPath(name)
    require(not p.is_absolute() and '..' not in p.parts and '\\' not in name, 'Unsafe path')
    return p.parts


def verify(root):
    q = root / 'docs/literature/twosamplemr-historical-handover'
    profile = json.loads((q / 'PROFILE.json').read_bytes())
    require(profile['status'] == 'UNFINISHED_HISTORICAL_TWOSAMPLEMR_HANDOVER'
            and profile['new_catalog_credit'] == 0 and not profile['whole_goal_complete']
            and not profile['live_normal_JAR_analyses_qualified'], 'Changed scope')
    for name, wanted in {**profile['source_inputs'], **profile['canonical_unchanged']}.items():
        require(pin(root.joinpath(*safe(name)).read_bytes()) == wanted, 'Changed source: ' + name)
    require(len(profile['canonical_unchanged']) == 27, 'Changed canonical population')
    for name, wanted in profile['packet_files'].items():
        require(pin((q / name).read_bytes()) == wanted, 'Changed packet file: ' + name)
    inventory = json.loads((q / 'INVENTORY.json').read_bytes())
    unique = {x['sha256'] for x in inventory.values()}
    require(len(inventory) == profile['logical_files'] and len(unique) == profile['unique_blobs'], 'Changed population')
    with zipfile.ZipFile(q / 'evidence.zip') as z:
        names = z.namelist()
        require(len(names) == len(set(names)) == len(unique)
                and set(names) == {'blobs/' + s for s in unique}, 'Changed ZIP members')
        total = 0
        for sha in unique:
            require(len(sha) == 64 and all(c in '0123456789abcdef' for c in sha), 'Malformed SHA')
            size = z.getinfo('blobs/' + sha).file_size
            total += size
            require(size <= 32 * 1024 * 1024 and total <= 256 * 1024 * 1024, 'Capacity exceeded')
            require(hashlib.sha256(z.read('blobs/' + sha)).hexdigest() == sha, 'Changed blob')
        for name, wanted in inventory.items():
            safe(name)
            require(len(z.read('blobs/' + wanted['sha256'])) == wanted['bytes'], 'Changed byte count')
        def read(name):
            return json.loads(z.read('blobs/' + inventory[name]['sha256']))
        normal = read('current/normal-gradle-3/COMPLETED.json')
        require(normal['exit_code'] == 0 and sum(v['tests'] for v in normal['reports'].values()) == 769
                and all(v[k] == 0 for v in normal['reports'].values() for k in ('failures', 'errors', 'skipped')), 'Normal tests failed')
        packaged = read('current/packaged-1/RESULT.json')
        require(set(packaged['hosts']) == {'windows21', 'windows25', 'linux21'}
                and all(v['exit_code'] == 0 and v['tests'] == 769 and v['skipped'] == 0
                        for v in packaged['hosts'].values()), 'Packaged tests failed')
        require(read('current/packaged-1/JAR-COMPARISON.json')['published_predecessor_payloads_unchanged'] == 22335, 'Predecessor changed')
        direct = read('current/independent-original-1/RESULT.json')
        require(direct['all_pass'] and len(direct['results']) == 48 and direct['full_original_returns'] == 120
                and direct['original_errors'] == 4 and all(v['exit_code'] == 0 for v in direct['results'].values()), 'Direct comparisons failed')
        require(len(read('current/fixture-2/PROFILE.json')['cases']) == 45, 'Changed fixture population')
    return dict(status=profile['status'], source_files=len(profile['source_inputs']), canonical_files=27,
                normal_and_each_packaged_JVM_tests=769, predecessor_payloads_unchanged=22335,
                earlier_quick_compiled_original_returns=120, original_errors=4,
                logical_files=len(inventory), unique_blobs=len(unique), new_catalog_credit=0,
                fresh_Java_R_execution=False, live_normal_JAR_analyses_qualified=False, whole_goal_complete=False)


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--out', type=Path, required=True)
    a = p.parse_args()
    require(__debug__ and sys.version_info >= (3, 11), 'Use Python3.11+ without -O')
    result = verify(Path(__file__).resolve().parents[2])
    a.out.mkdir(parents=True, exist_ok=False)
    (a.out / 'RESULT.json').write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8', newline='\n')
    print(json.dumps(result))


if __name__ == '__main__':
    main()
