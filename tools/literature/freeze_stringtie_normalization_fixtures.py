# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Freeze extracted-source arithmetic controls without relabeling them whole-StringTie runs."""
import hashlib
import json
from pathlib import Path
import stat
import zipfile

ROOT = Path(__file__).resolve().parents[2]
BASE = ROOT / 'build/literature-review/stringtie-runtime'


def pin(path):
    raw = path.read_bytes()
    return dict(bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest())


def main():
    controls = BASE / 'normalization-source-controls-1'
    capture = json.loads((controls / 'CAPTURED.json').read_bytes())
    assert capture['total_cases'] == 1008 and not capture['whole_StringTie_runs'] and not capture['implementation_credit']
    assert pin(ROOT / 'tools/literature/collect_stringtie_normalization.py') == capture['collector']
    target = ROOT / 'src/test/resources/org/jlinalg/sequence/stringtie-normalization'
    assert not target.exists()
    files = {}

    def add(path, name):
        assert path.is_file() and not path.is_symlink() and name not in files
        files[name] = path.read_bytes()

    add(controls / 'cases.tsv', 'cases.tsv'); add(controls / 'CAPTURED.json', 'CAPTURED.json')
    assert pin(controls / 'cases.tsv') == capture['cases']
    for edition in capture['editions']:
        version = edition['version']; folder = controls / version
        assert edition['cases'] == 144 and edition['process']['exit_code'] == edition['build']['exit_code'] == 0
        for path, expected in [(folder / 'original-values.tsv', edition['outputs']), (folder / 'original-expressions.cpp.txt', edition['expression']),
                               (folder / 'driver.cpp', edition['driver']), (ROOT / edition['source_relative'], edition['source'])]:
            assert pin(path) == expected
        for name in ['original-values.tsv', 'original-expressions.cpp.txt', 'driver.cpp', 'compile.stdout', 'compile.stderr', 'compile.process.json', 'run.stderr']:
            add(folder / name, version + '/' + name)
        add(ROOT / edition['source_relative'], version + '/original-stringtie.cpp')
        add(ROOT / edition['license_relative'], version + '/LICENSE')
    target.mkdir(); archive = target / 'original-normalization-arithmetic.zip'; members = {}
    with zipfile.ZipFile(archive, 'x', compression=zipfile.ZIP_DEFLATED, compresslevel=9) as z:
        for name, raw in sorted(files.items()):
            info = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0)); info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = (stat.S_IFREG | 0o644) << 16; z.writestr(info, raw)
            members[name] = dict(bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest())
    with zipfile.ZipFile(archive) as z:
        assert z.testzip() is None and len(z.namelist()) == len(set(z.namelist())) == len(members)
        for name, value in members.items():
            raw = z.read(name); assert dict(bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest()) == value
    result = dict(schema_version=1, status='captured-1008-original-source-normalization-arithmetic-fixtures',
                  collector=capture['collector'], freezer=pin(Path(__file__)), archive=pin(archive), archive_files=members,
                  cases=1008, editions=7, whole_StringTie_runs=False, implementation_credit=False,
                  warning='These are unmodified extracted source expressions in a C++ FE_TONEAREST harness, not1008 original StringTie fits. Original full-tool output families remain separate evidence. Nonfinite classes are retained; portable NaN payload/sign identity is not claimed.')
    (target / 'CAPTURE.json').write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8', newline='\n')
    (target / 'SHA256SUMS').write_text(''.join(pin(target / name)['sha256'] + '  ' + name + '\n' for name in ['CAPTURE.json', archive.name]), encoding='ascii', newline='\n')
    (target / '.gitattributes').write_text('original-normalization-arithmetic.zip -text\nCAPTURE.json -text\nSHA256SUMS -text\n', encoding='ascii', newline='\n')
    print(json.dumps(dict(status=result['status'], archive=pin(archive), members=len(members))))


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable extracted-source arithmetic fixture checks')
    main()
