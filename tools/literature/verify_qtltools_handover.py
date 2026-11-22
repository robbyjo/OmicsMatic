"""Verify saved development and worktree bytes; optionally restore copies. Run no analysis."""
from pathlib import Path, PurePosixPath
import argparse
import hashlib
import json
import re
import subprocess
import sys
import zipfile

FOLDER = 'docs/literature/handover-2026-11-21-qtltools'


def require(value, message):
    if not value:
        raise ValueError(message)


def pin(raw):
    return dict(bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest())


def safe(name):
    path = PurePosixPath(name)
    require(name and not path.is_absolute() and all(
        part not in {'.', '..'} and ':' not in part and '\\' not in part
        for part in path.parts), 'Unsafe retained path')
    return path.parts


def read_json(path):
    return json.loads(path.read_bytes())


def write_json(path, value):
    with path.open('x', encoding='utf-8', newline='\n') as stream:
        json.dump(value, stream, indent=2)
        stream.write('\n')


def archive(path):
    result = {}
    with zipfile.ZipFile(path) as source:
        entries = source.infolist()
        require(len(entries) <= 10000 and len({entry.filename for entry in entries}) == len(entries),
                'Duplicate or excessive archive entries')
        require(sum(entry.file_size for entry in entries) <= 256 * 1024 * 1024,
                'Archive exceeds total capacity')
        for entry in entries:
            safe(entry.filename)
            require(re.fullmatch(r'blobs/[0-9a-f]{64}', entry.filename) is not None
                    and entry.file_size <= 64 * 1024 * 1024,
                    'Invalid or excessive retained member')
            raw = source.read(entry)
            require(hashlib.sha256(raw).hexdigest() == entry.filename[6:],
                    'Changed content-addressed member')
            result[entry.filename] = raw
    return result


def git_blobs(root, executable, wanted):
    ordered = sorted(wanted)
    if not ordered:
        return {}
    for oid in ordered:
        require(re.fullmatch('[0-9a-f]{40}', oid) is not None, 'Invalid Git object')
    process = subprocess.run([executable, '--no-replace-objects', 'cat-file', '--batch'],
        cwd=root, input=('\n'.join(ordered) + '\n').encode(),
        stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=120)
    require(process.returncode == 0, 'Git object reader failed')
    result = {}; cursor = 0
    for oid in ordered:
        end = process.stdout.find(b'\n', cursor)
        header = process.stdout[cursor:end].decode().split()
        require(end >= cursor and len(header) == 3 and header[:2] == [oid, 'blob'],
                'Required baseline Git blob unavailable; fetch full main history')
        size = int(header[2]); require(0 <= size <= 64 * 1024 * 1024, 'Excessive Git blob')
        raw = process.stdout[end + 1:end + 1 + size]
        require(hashlib.sha1(b'blob ' + str(size).encode() + b'\0' + raw).hexdigest() == oid,
                'Changed Git blob')
        cursor = end + size + 2; result[oid] = raw
    require(cursor == len(process.stdout), 'Trailing Git object output')
    return result


def verify(root, out, executable, restore):
    folder = root / FOLDER; profile = read_json(folder / 'PROFILE.json')
    require(profile['status'] == 'UNFINISHED_QTLTOOLS_WORKFLOW_HANDOVER'
            and profile['production_source_changed'] is False
            and profile['workflow_qualified'] is False
            and profile['new_catalog_credit'] == 0
            and profile['whole_goal_complete'] is False,
            'Development snapshot promoted to qualified implementation')
    require(profile['catalog'] == dict(papers=250, scoped_families=145,
        native=26, upstream=119, reviewed=1630, pending=1092), 'Different catalog checkpoint')
    for name, wanted in {**profile['source_inputs'], **profile['canonical_unchanged']}.items():
        require(pin(root.joinpath(*safe(name)).read_bytes()) == wanted, 'Changed source/canonical: ' + name)
    for name, wanted in profile['packet_files'].items():
        require(pin(folder.joinpath(*safe(name)).read_bytes()) == wanted, 'Changed handover file: ' + name)
    prior = profile['prior_archive']; prior_path = root.joinpath(*safe(prior['path']))
    require(pin(prior_path.read_bytes()) == prior['pin'], 'Changed historical archive')
    archives = dict(prior=archive(prior_path), delta=archive(folder / 'deltas.zip'))
    states = read_json(folder / 'WORKTREES.json')
    require(len(states) == profile['historical_worktrees'] == 61, 'Different worktree population')
    wanted = {entry['storage']['oid'] for state in states for entry in state['files'].values()
              if entry is not None and entry['storage']['kind'] == 'git'}
    blobs = git_blobs(root, executable, wanted)
    restored_files = 0
    for number, state in enumerate(states):
        target_root = out / 'worktrees' / f'{number:02d}'
        if restore:
            target_root.mkdir(parents=True); write_json(target_root / 'STATE.json', state)
        entries = dict(state['files']); entries['INDEX.patch'] = state['index_patch']
        for name, entry in entries.items():
            safe(name)
            if entry is None:
                continue
            storage = entry['storage']; kind = storage['kind']
            require(kind in {'git', 'prior', 'delta'}, 'Unknown retained storage')
            raw = blobs[storage['oid']] if kind == 'git' else archives[kind][storage['member']]
            require(pin(raw) == {key: entry[key] for key in ['bytes', 'sha256']}, 'Changed variant: ' + name)
            if name != 'INDEX.patch':
                restored_files += 1
            if restore:
                target = target_root / 'INDEX.patch' if name == 'INDEX.patch' else target_root.joinpath('files', *safe(name))
                target.parent.mkdir(parents=True, exist_ok=True)
                with target.open('xb') as stream:
                    stream.write(raw)
    evidence = archive(folder / 'evidence.zip')
    inventory = read_json(folder / 'INVENTORY.json')
    for name, wanted in inventory.items():
        raw = evidence['blobs/' + wanted['sha256']]
        require(pin(raw) == wanted, 'Changed evidence: ' + name)
        if restore:
            target = out.joinpath('evidence', *safe(name)); target.parent.mkdir(parents=True, exist_ok=True)
            with target.open('xb') as stream:
                stream.write(raw)
    require(restored_files == profile['git_visible_files'], 'Different variant population')
    mapping = read_json(root / 'tools/literature/qtltools_workflow_development/RESTORE.json')
    require(len(mapping) == 6, 'Different draft population')
    for name, row in mapping.items():
        safe(name); require(pin(root.joinpath(*safe(row['draft'])).read_bytes()) == row['pin'], 'Changed draft')
    return dict(status='PASS_SAVED_QTLTOOLS_HANDOVER_BYTES_AND_SCOPE_ONLY',
        historical_worktrees=len(states), git_visible_files=restored_files,
        staged_patches=len(states), draft_files=len(mapping), evidence_files=len(inventory),
        catalog=profile['catalog'], production_source_changed=False,
        workflow_qualified=False, scientific_analyses_executed=0,
        new_catalog_credit=0, whole_goal_complete=False, restored=restore)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path, default=Path(__file__).resolve().parents[2])
    parser.add_argument('--out', type=Path, required=True)
    parser.add_argument('--git', default='git')
    parser.add_argument('--restore', action='store_true')
    args = parser.parse_args()
    require(__debug__ and sys.version_info >= (3, 11), 'Use Python3.11+ without -O')
    root = args.root.resolve(); out = args.out.resolve()
    require(not out.exists(), 'Use a NEW output directory')
    for protected in [root / FOLDER, root / 'tools/literature/qtltools_workflow_development']:
        require(not out.is_relative_to(protected) and not protected.is_relative_to(out), 'Output overlaps saved inputs')
    out.mkdir(parents=True)
    try:
        result = verify(root, out, args.git, args.restore)
        write_json(out / 'RESULT.json', result); print(json.dumps(result)); return 0
    except Exception as error:
        with (out / 'FAILURE.txt').open('x', encoding='utf-8') as stream:
            stream.write(str(error) + '\n')
        print('FAIL2 saved handover: ' + str(error), file=sys.stderr); return 2


if __name__ == '__main__':
    raise SystemExit(main())
