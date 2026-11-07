# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Copy complete retained setup receipts for portable native-reader regression controls."""
from pathlib import Path
import json
import zipfile

ROOT = Path(__file__).resolve().parents[2]

def main():
    source = ROOT / 'build/literature-review/stringtie-runtime/setup-validation-1/reports'
    destination = ROOT / 'src/test/resources/org/jlinalg/cli/stringtie-runtime-receipts.zip'
    assert not destination.exists()
    names = [kind + '-' + version for kind in ['install', 'recheck'] for version in ['1.3.0', '1.3.6', '2.1.2', '2.1.7', '2.2.1', '3.0.0', '3.0.3']] + ['download-report']
    entries = {}
    for name in names:
        for path in sorted((source / name).iterdir()):
            assert path.is_file()
            entries[name + '/' + path.name] = path.read_bytes()
        assert json.loads(entries[name + '/COMPLETED.json'])['status'] == 'COMPLETE_SETUP_ONLY_NOT_ADMISSION'
    # An independently exported JAR kit's actual standalone-install receipt.
    for path in sorted((ROOT / 'build/literature-review/stringtie-runtime/native-intake-setup-matrix-1/standalone-report').iterdir()):
        entries['standalone-report/' + path.name] = path.read_bytes()
    assert len(entries) == 73
    destination.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(destination, 'x') as archive:
        for name, raw in sorted(entries.items()):
            info = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0)); info.compress_type = zipfile.ZIP_DEFLATED; info.external_attr = 0o100644 << 16
            archive.writestr(info, raw)
    print(destination)

if __name__ == '__main__': main()
