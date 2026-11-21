"""Verify the unfinished graph handover and historical variants; run no analysis."""
from pathlib import Path, PurePosixPath
import argparse
import hashlib
import json
import re
import subprocess
import sys
import zipfile


def require(ok, message):
    if not ok:
        raise ValueError(message)


def pin(raw):
    return dict(bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest())


def safe(name):
    path = PurePosixPath(name)
    require(bool(name) and not path.is_absolute() and not any(
        part in {'..', '.'} or ':' in part or '\\' in part for part in path.parts
    ), 'Unsafe retained path')
    return path.parts


def archive_values(path, expected, out):
    require(len(expected) <= 10000, 'Too many retained blobs')
    total = 0
    with zipfile.ZipFile(path) as archive:
        names = archive.namelist()
        require(len(names) == len(set(names)) == len(expected)
                and set(names) == set(expected), 'Different archive population')
        for name, wanted in sorted(expected.items()):
            safe(name)
            require(re.fullmatch(r'blobs/[0-9a-f]{64}', name) is not None,
                    'Malformed blob name')
            size = archive.getinfo(name).file_size
            total += size
            require(0 <= size <= 64 * 1024 * 1024 and total <= 256 * 1024 * 1024,
                    'Archive exceeds declared capacity')
            raw = archive.read(name)
            require(pin(raw) == wanted, 'Changed retained blob')
            if out is not None:
                target = out.joinpath(*safe(name))
                target.parent.mkdir(parents=True, exist_ok=True)
                with target.open('xb') as stream:
                    stream.write(raw)


def verify(root, out, git, restore):
    folder = root / 'docs/literature/handover-2026-11-21-graph'
    profile = json.loads((folder / 'PROFILE.json').read_bytes())
    require(profile['status'] == 'UNFINISHED_MONOCLE100_GRAPH_HANDOVER'
            and profile['new_catalog_credit'] == 0
            and profile['whole_goal_complete'] is False
            and profile['production_source_build_site_changed'] is False,
            'Different handover scope')
    for name, wanted in {**profile['source_inputs'], **profile['canonical_unchanged']}.items():
        require(pin(root.joinpath(*safe(name)).read_bytes()) == wanted,
                'Changed source/canonical bytes: ' + name)
    for name, wanted in profile['packet_files'].items():
        require(pin(folder.joinpath(*safe(name)).read_bytes()) == wanted,
                'Changed packet file: ' + name)
    inventory = json.loads((folder / 'INVENTORY.json').read_bytes())
    evidence = {}
    for name, wanted in inventory.items():
        safe(name)
        key = 'blobs/' + wanted['sha256']
        require(key not in evidence or evidence[key] == wanted, 'Conflicting blob')
        evidence[key] = wanted
    archive_values(folder / 'evidence.zip', evidence, None)
    with zipfile.ZipFile(folder / 'evidence.zip') as archive:
        def raw(name):
            wanted = inventory[name]
            value = archive.read('blobs/' + wanted['sha256'])
            require(pin(value) == wanted, 'Changed logical evidence file')
            return value
        def read(name):
            return json.loads(raw(name))
        reference = read('current/original-reference-1/RESULT.json')
        require(reference['status'] == 'PASS_UNCHANGED_ORIGINAL100_GRAPH_REFERENCE_CASES'
                and not reference['adapter_worker_sourced'], 'Different original-reference scope')
        cases = reference['cases']
        require(len(cases) == 21 and sum(c['expected_cli_exit'] == 0 for c in cases.values()) == 16
                and sum(c['source_error'] is not None for c in cases.values()) == 3
                and sum(c['declared_convergence_policy_refused'] for c in cases.values()) == 2,
                'Different original-reference expectations')
        require(read('current/original-reference-1/PROCESS.json')['exit_code'] == 0,
                'Original reference process incomplete')
        for name in ['pilot-learn-1', 'pilot-run-1']:
            require(read('current/' + name + '/PROCESS.json')['exit_code'] == 0,
                    'Different earlier quick-pilot outcome')
            require(read('current/' + name + '/STARTED.json')['quick_compiled_only'] is True,
                    'Pilot incorrectly presented as packaged qualification')
        audit = read('current/pilot-run-1/capture/results/audit.json')
        require((audit['cells'], audit['principal_points'], audit['principal_edges']) == (27, 5, 4)
                and audit['pseudotime_computed'] and audit['scientific_test_admitted'] is False,
                'Different earlier quick-pilot scope')
        require(profile['final_source_live_qualification'] is False
                and profile['normal_graph_JAR_qualified'] is False,
                'Unqualified final graph source promoted')
        if restore:
            for name in inventory:
                target = out / 'evidence' / Path(*safe(name))
                target.parent.mkdir(parents=True, exist_ok=True)
                with target.open('xb') as stream:
                    stream.write(raw(name))
    states = json.loads((folder / 'WORKTREES.json').read_bytes())
    require(len(states) == profile['historical_worktrees'] == 52
            and len({state['root'] for state in states}) == 52,
            'Different historical worktree population')
    base = profile['baseline']
    require(re.fullmatch(r'[0-9a-f]{40}', base) is not None, 'Invalid baseline')
    subprocess.run([git, '--no-replace-objects', 'cat-file', '-e', base + '^{commit}'],
                   cwd=root, check=True, stdin=subprocess.DEVNULL,
                   stdout=subprocess.DEVNULL)
    delta = {}
    git_blobs = {}
    logical = from_git = from_delta = 0
    for state in states:
        require(re.fullmatch(r'[0-9a-f]{40}', state['head']) is not None,
                'Malformed historical head')
        bytes.fromhex(state['status_hex'])
        for name, wanted in state['files'].items():
            safe(name)
            if wanted is None:
                continue
            logical += 1
            expected = {key: wanted[key] for key in ['bytes', 'sha256']}
            storage = wanted['storage']
            if storage['type'] == 'git-blob':
                oid = storage['oid']
                require(re.fullmatch(r'[0-9a-f]{40}', oid) is not None,
                        'Invalid Git blob identity')
                require(oid not in git_blobs or git_blobs[oid] == expected,
                        'Conflicting Git blob pins')
                git_blobs[oid] = expected
                from_git += 1
            else:
                require(storage['type'] == 'archive' and storage['member'] ==
                        'blobs/' + wanted['sha256'], 'Different delta mapping')
                delta[storage['member']] = expected
                from_delta += 1
        staged = state['index_patch']
        require(staged['member'] == 'blobs/' + staged['sha256'], 'Unsafe index patch')
        delta[staged['member']] = {key: staged[key] for key in ['bytes', 'sha256']}
    archive_values(folder / 'deltas.zip', delta, None)
    with subprocess.Popen([git, '--no-replace-objects', 'cat-file', '--batch'],
                          cwd=root, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                          stderr=subprocess.PIPE) as process:
        try:
            for oid, wanted in sorted(git_blobs.items()):
                require(0 <= wanted['bytes'] <= 64 * 1024 * 1024,
                        'Git blob exceeds capacity')
                process.stdin.write((oid + '\n').encode('ascii'))
                process.stdin.flush()
                header = process.stdout.readline().decode('ascii').strip().split()
                require(header == [oid, 'blob', str(wanted['bytes'])],
                        'Missing or changed historical Git blob')
                value = process.stdout.read(wanted['bytes'])
                require(pin(value) == wanted and process.stdout.read(1) == b'\n',
                        'Changed baseline Git bytes')
            process.stdin.close()
            require(process.wait(timeout=30) == 0, 'Git blob reader failed')
        except BaseException:
            process.kill()
            process.wait()
            raise
    require((logical, from_git, from_delta, len(delta)) ==
            (profile['git_visible_files'], profile['files_from_base_git_blobs'],
             profile['files_from_delta_archive'], profile['unique_delta_blobs']),
            'Different historical snapshot totals')
    if restore:
        with zipfile.ZipFile(folder / 'deltas.zip') as archive:
            for index, state in enumerate(states):
                target_root = out / 'worktrees' / f'{index:02d}'
                target_root.mkdir(parents=True, exist_ok=False)
                (target_root / 'STATE.json').write_text(
                    json.dumps(state, indent=2) + '\n', encoding='utf-8', newline='\n')
                for name, wanted in state['files'].items():
                    if wanted is None:
                        continue
                    storage = wanted['storage']
                    if storage['type'] == 'archive':
                        value = archive.read(storage['member'])
                    else:
                        value = subprocess.check_output(
                            [git, '--no-replace-objects', 'cat-file', 'blob', storage['oid']],
                            cwd=root, stdin=subprocess.DEVNULL)
                    require(pin(value) == {key: wanted[key] for key in ['bytes', 'sha256']},
                            'Changed restoration bytes')
                    target = target_root / 'files' / Path(*safe(name))
                    target.parent.mkdir(parents=True, exist_ok=True)
                    with target.open('xb') as stream:
                        stream.write(value)
                with (target_root / 'INDEX.patch').open('xb') as stream:
                    stream.write(archive.read(state['index_patch']['member']))
    return dict(status='PASS_SAVED_UNFINISHED_GRAPH_HANDOVER_BYTES_ONLY',
                historical_worktrees=52, git_visible_files=logical,
                files_from_base_git=from_git, files_from_delta_archive=from_delta,
                logical_evidence_files=len(inventory), new_catalog_credit=0,
                whole_goal_complete=False, final_source_live_qualification=False,
                scientific_analyses_run=0, restored=restore)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, required=True)
    parser.add_argument('--git', default='git')
    parser.add_argument('--restore', action='store_true')
    args = parser.parse_args()
    require(__debug__ and sys.version_info >= (3, 11), 'Use Python3.11+ without -O')
    root = Path(__file__).resolve().parents[3]
    out = args.out.resolve()
    require(not out.exists(), 'Use NEW output')
    out.mkdir(parents=True, exist_ok=False)
    try:
        result = verify(root, out, args.git, args.restore)
        with (out / 'RESULT.json').open('x', encoding='utf-8', newline='\n') as stream:
            json.dump(result, stream, indent=2)
            stream.write('\n')
        print(json.dumps(result))
        return 0
    except Exception as error:
        (out / 'FAILURE.txt').write_text(str(error) + '\n', encoding='utf-8')
        print('FAIL2 saved handover verification: ' + str(error), file=sys.stderr)
        return 2


if __name__ == '__main__':
    raise SystemExit(main())
