#!/usr/bin/env python3
"""Verify this prepared-workflow qualification checkpoint; optionally restore exact artificial evidence.

Integrity only: no R execution, RDS interpretation, refit, signature or scientific
admission. Use each capture's original exact JAR for its separate Java VERIFY.
"""
from pathlib import Path, PurePosixPath
import argparse
import hashlib
import json
import re
import zipfile

ROOT = Path(__file__).resolve().parents[3]
PACKET = ROOT / 'docs/literature/skat225-qualification'


def require(condition, message):
    if not condition:
        raise ValueError(message)


def pin(data):
    return {'bytes': len(data), 'sha256': hashlib.sha256(data).hexdigest()}


def member(name):
    path = PurePosixPath(name)
    require(isinstance(name, str) and str(path) == name and not path.is_absolute()
            and all(p not in ('', '.', '..') and ':' not in p and '\\' not in p
                    for p in path.parts), 'Unsafe relative member')
    return path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--restore', type=Path, help='NEW directory; no overwrite')
    parser.add_argument('--prefix', help='Only restore this exact evidence subtree')
    args = parser.parse_args()
    require(args.restore is not None or args.prefix is None, '--prefix requires --restore')
    if args.prefix:
        member(args.prefix)
    status = json.loads((PACKET / 'STATUS.json').read_bytes())
    require(status['whole_goal_complete'] is False and status['new_catalog_credit'] == 0,
            'Unexpected checkpoint scope')
    for name, expected in status['file_pins'].items():
        path = ROOT.joinpath(*member(name).parts)
        require(path.is_file() and not path.is_symlink(), 'Missing regular pinned file: ' + name)
        require(pin(path.read_bytes()) == expected, 'Changed checkpoint file: ' + name)
    info = json.loads((PACKET / 'EVIDENCE.json').read_bytes())
    invalidated = json.loads((PACKET / 'INVALIDATED-EVIDENCE.json').read_bytes())
    archive = PACKET / info['archive']
    require(pin(archive.read_bytes()) == info['archive_pin'], 'Archive hash differs')
    with zipfile.ZipFile(archive) as z:
        names = z.namelist()
        require(len(names) == len(set(names)), 'Duplicate archive names')
        require(pin(z.read('manifest.json')) == info['manifest_pin'], 'Manifest hash differs')
        inventory = json.loads(z.read('manifest.json'))['files']
        objects = {}
        for name, expected in inventory.items():
            member(name)
            require(re.fullmatch('[0-9a-f]{64}', expected['sha256']) is not None
                    and type(expected['bytes']) is int and 0 <= expected['bytes'] <= 268435456,
                    'Invalid object description')
            key = 'objects/' + expected['sha256']
            require(key not in objects or objects[key] == expected, 'Conflicting object size')
            objects[key] = expected
        require(set(names) == set(objects) | {'manifest.json'}, 'Archive population differs')
        for key, expected in objects.items():
            require(z.getinfo(key).file_size == expected['bytes'], 'Object size differs')
            require(pin(z.read(key)) == expected, 'Changed object: ' + key)
        restored = 0
        if args.restore is not None:
            chosen = {n: p for n, p in inventory.items()
                      if not args.prefix or n == args.prefix or n.startswith(args.prefix + '/')}
            require(chosen, 'No matching evidence subtree')
            target = args.restore.absolute()
            require(not target.exists() and not target.is_symlink(), 'Restore directory must be NEW')
            target.mkdir(parents=True)
            target = target.resolve(strict=True)
            for name, expected in chosen.items():
                path = target.joinpath(*member(name).parts)
                path.parent.mkdir(parents=True, exist_ok=True)
                require(path.parent.resolve(strict=True).is_relative_to(target), 'Restore escaped root')
                with path.open('xb') as stream:
                    stream.write(z.read('objects/' + expected['sha256']))
                require(pin(path.read_bytes()) == expected, 'Restored bytes differ')
                restored += 1
    print(json.dumps({'status': 'PASS_CHECKPOINT_AND_SELECTED_ARCHIVE_INTEGRITY',
                      'pinned_files': len(status['file_pins']), 'evidence_files': len(inventory),
                      'unique_objects': len(objects), 'restored_files': restored,
                      'retained_invalidated_capture_prefixes': invalidated['invalidated_capture_prefixes'],
                      'invalidated_result_action': 'Do not use its admitted export;legacy integrity does not revalidate science. Preserve unchanged and use corrected NEW output.',
                      'scientific_admission': False, 'new_catalog_credit': 0,
                      'whole_goal_complete': False}, indent=2))


if __name__ == '__main__':
    main()
