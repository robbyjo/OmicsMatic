# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Freeze six complete original Salmon setup reports for portable native replay tests."""
import hashlib
import json
from pathlib import Path
import zipfile

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / 'build/literature-review/salmon-runtime/managed-setup-development-2'
OUT = ROOT / 'src/test/resources/org/jlinalg/cli/salmon-setup'


def pin(path):
    raw = path.read_bytes()
    return dict(bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest())


def main():
    if not __debug__:
        raise RuntimeError('Never disable fixture integrity checks')
    assert not OUT.exists()
    completed = json.loads((SOURCE / 'COMPLETED.json').read_bytes())
    assert completed['status'] == 'passed-current-Salmon-extraction-five-rechecks-and-real-authorization-integrity-controls'
    assert all(pin(ROOT / name) == value for name, value in completed['source_pins'].items())
    reports = [SOURCE / 'fresh-1.9.0', *[SOURCE / ('recheck-' + v) for v in ['1.0.0', '1.1.0', '1.5.2', '1.8.0', '1.9.0']]]
    OUT.mkdir(parents=True)
    rows = []
    archive = OUT / 'original-setup-reports.zip'
    with zipfile.ZipFile(archive, 'x', compression=zipfile.ZIP_DEFLATED, compresslevel=9) as target:
        for report in reports:
            marker = json.loads((report / 'COMPLETED.json').read_bytes())
            assert marker['status'] == 'COMPLETE_SETUP_ONLY' and marker['manifest'] == pin(report / 'manifest.json')
            manifest = json.loads((report / 'manifest.json').read_bytes())
            actual = {p.relative_to(report).as_posix() for p in report.rglob('*')}
            assert actual == set(manifest['files']) | {'manifest.json', 'COMPLETED.json'}
            for name, item in manifest['files'].items():
                path = report / name
                assert not path.is_symlink()
                if item['kind'] == 'directory':
                    assert path.is_dir()
                else:
                    assert pin(path) == {k: item[k] for k in ['bytes', 'sha256']}
            total = 0
            for name in sorted(actual):
                path = report / name
                info = zipfile.ZipInfo(report.name + '/' + name + ('/' if path.is_dir() else ''), (2026, 11, 3, 0, 0, 0))
                info.create_system = 3
                info.external_attr = ((0o40755 if path.is_dir() else 0o100644) << 16)
                raw = b'' if path.is_dir() else path.read_bytes()
                target.writestr(info, raw, compress_type=zipfile.ZIP_DEFLATED, compresslevel=9)
                total += len(raw)
            rows.append(dict(name=report.name, profile=manifest['profile'], installed=manifest['installed'],
                             manifest=marker['manifest'], nodes=len(actual), logical_bytes=total))
    provenance = dict(schema_version=1, status='complete-original-setup-reports-for-portable-native-tests-only',
                      source_checkpoint=pin(SOURCE / 'COMPLETED.json'), source_pins=completed['source_pins'],
                      fixture_producer=pin(Path(__file__)), archive=pin(archive), reports=rows,
                      scientific_runtime_admitted=False, user_analysis_admitted=False,
                      scope='Six historical reports from five exact editions: one new offline installation and five existing-home rechecks. '
                            'All complete input/index/result/process files are copied without changing content. Producer Linux paths are text. '
                            'Fixtures reuse one synthetic population below burn-in; this is not current-host or biological admission.')
    (OUT / 'provenance.json').write_text(json.dumps(provenance, indent=2) + '\n', encoding='utf-8', newline='\n')
    (OUT / '.gitattributes').write_text('* -text\n', encoding='ascii', newline='\n')
    print(json.dumps(dict(archive=pin(archive), reports=len(rows), logical_bytes=sum(r['logical_bytes'] for r in rows))))


if __name__ == '__main__':
    main()
