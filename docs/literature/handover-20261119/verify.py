"""Read-only integrity verification of the historical handover archive."""
from pathlib import Path
import hashlib
import json
import sys
import zipfile


def require(condition, message):
    if not condition:
        raise ValueError(message)


def check_pin(data, expected, name):
    require(len(data) == expected['bytes'], 'Length mismatch: ' + name)
    require(hashlib.sha256(data).hexdigest() == expected['sha256'],
            'SHA-256 mismatch: ' + name)


def main():
    folder = Path(__file__).resolve().parent
    inventory = (folder / 'OVERLAYS.json').read_bytes()
    archive = (folder / 'historical-overlays.zip').read_bytes()
    check_pin(inventory, {'bytes': 783256, 'sha256':
        '8ce0f7e6f527f9dedd2f011058f167d67a2c4797326f0737e9b85b397db7ef4b'}, 'inventory')
    check_pin(archive, {'bytes': 7622243, 'sha256':
        '739fe6f934b3c3fbed09b18fd705555cb2cda17c9262b2d77de466df067fb964'}, 'archive')
    data = json.loads(inventory)
    require(len(data['worktrees']) == 21, 'Worktree count differs')
    files = [row for tree in data['worktrees'] for row in tree['files']]
    require(len(files) == 2248, 'Inventory file count differs')
    expected = {row['archive_member']: row for row in files if 'archive_member' in row}
    require(len(expected) == 382, 'Archived payload count differs')
    with zipfile.ZipFile(folder / 'historical-overlays.zip') as z:
        names = z.namelist()
        require(len(names) == len(set(names)), 'Duplicate archive members')
        require(set(names) == set(expected), 'Archive member set differs')
        require(z.testzip() is None, 'Archive CRC failure')
        for name, row in expected.items():
            require(not name.startswith(('/', '\\')) and '..' not in Path(name).parts,
                    'Unsafe member name: ' + name)
            check_pin(z.read(name), row, name)
    print(json.dumps({'status': 'PASS_HISTORICAL_OVERLAY_ARCHIVE_INTEGRITY',
        'worktrees': 21, 'inventory_files': 2248, 'archived_payloads': 382,
        'scientific_models_rerun': False, 'files_restored': False}))


if __name__ == '__main__':
    try:
        main()
    except FileNotFoundError as error:
        print('SKIP3: restore the missing handover file: ' + str(error), file=sys.stderr)
        raise SystemExit(3)
    except (OSError, ValueError, KeyError, zipfile.BadZipFile) as error:
        print('FAIL2: ' + str(error), file=sys.stderr)
        raise SystemExit(2)
