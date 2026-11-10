# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Run privately retained original KING workflows, preserving every raw report and fallback."""
import argparse
import json
import subprocess
import traceback
from pathlib import Path

from setup_deseq2_reference import ROOT, pin, save
from probe_king_runtime import BASE, DISTRO, linux


def subset_bed(source, target, count):
    """Declared first-N synthetic transport control; never modify the official example."""
    rows = source.with_suffix('.fam').read_bytes().splitlines(keepends=True)
    target.with_suffix('.fam').write_bytes(b''.join(rows[:count]))
    target.with_suffix('.bim').write_bytes(source.with_suffix('.bim').read_bytes())
    m = len(source.with_suffix('.bim').read_bytes().splitlines())
    width, small = (len(rows)+3)//4, (count+3)//4
    data = source.with_suffix('.bed').read_bytes()
    assert data[:3] == b'\x6c\x1b\x01' and len(data) == 3+m*width
    result = bytearray(data[:3])
    for i in range(m):
        row = bytearray(data[3+i*width:3+i*width+small])
        if count % 4:
            row[-1] &= (1 << (2*(count % 4)))-1
        result.extend(row)
    target.with_suffix('.bed').write_bytes(result)


def append_duplicate(source, target):
    rows = source.with_suffix('.fam').read_bytes().splitlines(keepends=True)
    target.with_suffix('.fam').write_bytes(b''.join(rows)+b'TEST_DUP COPY_OF_FIRST 0 0 1 -9\n')
    target.with_suffix('.bim').write_bytes(source.with_suffix('.bim').read_bytes())
    n = len(rows)
    data = source.with_suffix('.bed').read_bytes()
    width, new_width = (n+3)//4, (n+4)//4
    result = bytearray(data[:3])
    for start in range(3, len(data), width):
        old = data[start:start+width]
        row = bytearray(new_width)
        for sample in range(n+1):
            index = 0 if sample == n else sample
            code = (old[index//4] >> (2*(index % 4))) & 3
            row[sample//4] |= code << (2*(sample % 4))
        result.extend(row)
    target.with_suffix('.bed').write_bytes(result)


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--out', type=Path, required=True)
    a = p.parse_args()
    out = a.out.resolve()
    assert not out.exists() and out.is_relative_to(BASE)
    out.mkdir(parents=True)
    intake = BASE/'king-source-intake-1'
    original = intake/'ex-original/ex'
    executables = dict(windows=intake/'Windows-king-original/king.exe', linux=intake/'Linux-king-original/king')
    small = out/'first-four'
    subset_bed(original, small, 4)
    founders = out/'first-four-founders'
    subset_bed(original, founders, 4)
    # Explicit separate control: erase pedigree links only in this newly named control.
    rows = [row.split() for row in founders.with_suffix('.fam').read_text().splitlines()]
    founders.with_suffix('.fam').write_text(''.join(' '.join([r[0], r[1], '0', '0', r[4], r[5]])+'\n' for r in rows), encoding='utf8')
    duplicate = out/'appended-duplicate'
    append_duplicate(original, duplicate)
    inputs = [prefix.with_suffix('.'+extension) for prefix in [original, small, founders, duplicate] for extension in ['bed', 'bim', 'fam']]
    before = {str(path): pin(path) for path in [*executables.values(), *inputs]}
    records = []
    state = dict(status='FAIL2_ORIGINAL_KING_WORKFLOW_COLLECTION', runtime_admitted=False,
                 project_analysis_admitted=False, new_catalog_credit=False, whole_goal_complete=False)
    try:
        plan = [
            ('kinship', original, ['--kinship']),
            ('kinship-degree2', original, ['--kinship', '--degree', '2']),
            ('kinship-degree3', original, ['--kinship', '--degree', '3']),
            ('unrelated-degree2', original, ['--unrelated', '--degree', '2']),
            ('unrelated-degree3', original, ['--unrelated', '--degree', '3']),
            ('ibdseg', original, ['--ibdseg']),
            ('duplicate', original, ['--duplicate']),
            ('related', original, ['--related']),
            ('four-ibdseg-source-rejection', small, ['--ibdseg']),
            ('four-founders-ibdseg-fallback', founders, ['--ibdseg']),
            ('four-founders-related-fallback', founders, ['--related']),
            ('appended-duplicate', duplicate, ['--duplicate']),
        ]
        for platform in ['windows', 'linux']:
            convert = linux if platform == 'linux' else lambda path: str(path.resolve())
            for name, prefix, flags in plan:
                folder = out/(platform+'-'+name)
                folder.mkdir()
                arguments = ['-b', convert(prefix.with_suffix('.bed')), *flags, '--cpus', '1', '--prefix', convert(folder/'original')]
                if platform == 'windows':
                    command = [str(executables[platform]), *arguments]
                else:
                    command = ['wsl.exe', '-d', DISTRO, '--cd', linux(folder), '--exec', '/usr/bin/env', '-i',
                               'PATH=/usr/bin:/bin', 'LANG=C', 'LC_ALL=C', linux(executables[platform]), *arguments]
                with (folder/'stdout').open('xb') as so, (folder/'stderr').open('xb') as se:
                    try:
                        result = subprocess.run(command, cwd=folder, stdin=subprocess.DEVNULL, stdout=so, stderr=se, timeout=240)
                        code = result.returncode
                    except subprocess.TimeoutExpired:
                        code = 'TIMEOUT'
                record = dict(platform=platform, mode=name, command=command, exit_code=code,
                              inputs={extension: pin(prefix.with_suffix('.'+extension)) for extension in ['bed', 'bim', 'fam']})
                record['files'] = {path.name: pin(path) for path in sorted(folder.iterdir()) if path.is_file()}
                save(folder/'process.json', record)
                records.append(record)
                save(out/'PROGRESS.json', dict(records=records))
                print(json.dumps(dict(platform=platform, mode=name, exit_code=code, files=list(record['files']))), flush=True)
                expected = 1 if name == 'four-ibdseg-source-rejection' else 0
                assert code == expected, ('Original process differs from declared outcome; retain evidence', platform, name, code, expected)
        assert all(pin(Path(name)) == value for name, value in before.items())
        state.update(status='PASS_ORIGINAL_KING232_WORKFLOW_COLLECTION_ONLY', records=records,
                     complete_input_binary_pins=before, producer=pin(Path(__file__)),
                     scope='Original-only private research runs. Exit zero alone does not admit a requested method; fallbacks, populations, coordinates and numerical fields require separate verification.')
    except Exception as error:
        state.update(error=repr(error), records=records)
        (out/'error.traceback').write_text(traceback.format_exc(), encoding='utf8')
    save(out/'COMPLETED.json', state)
    print(state['status'], state.get('error', '')[:800], flush=True)
    return 0 if state['status'].startswith('PASS_') else 2


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable preservation checks')
    raise SystemExit(main())
