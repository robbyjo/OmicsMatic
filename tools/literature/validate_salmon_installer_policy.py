# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Source-pin Salmon installer policy controls on Windows and Linux; no scientific admission."""
import argparse
import concurrent.futures
import json
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools/literature'))
from validate_mashr_native import linux, pin


def write(path, value):
    path.write_text(json.dumps(value, indent=2) + '\n', encoding='utf-8', newline='\n')


def main():
    if not __debug__:
        raise RuntimeError('Never disable policy checks')
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args()
    out = args.out.resolve()
    assert out.is_relative_to((ROOT / 'build/literature-review/salmon-runtime').resolve()) and not out.exists()
    out.mkdir()
    kit = ROOT / 'src/main/resources/org/jlinalg/omics/salmon-runtime-setup'
    paths = [Path(__file__), ROOT / 'tools/literature/test_install_salmon.py', ROOT / 'tools/literature/install_salmon.py',
             *sorted(p for p in kit.rglob('*') if p.is_file())]
    sources = {p.relative_to(ROOT).as_posix(): pin(p) for p in paths}
    script = ROOT / 'tools/literature/test_install_salmon.py'
    commands = [('windows', [sys.executable, '-I', '-B', str(script), '--out', str(out / 'windows')], 2),
                ('linux', ['wsl.exe', '-d', 'JLinAlg-Omics-SAIGE-20261026', '--exec', '/usr/bin/python3', '-I', '-B', linux(script), '--out', linux(out / 'linux')], 0)]

    def check(spec):
        name, command, skipped = spec
        with (out / (name + '.stdout')).open('xb') as stdout, (out / (name + '.stderr')).open('xb') as stderr:
            result = subprocess.run(command, cwd=ROOT, stdin=subprocess.DEVNULL, stdout=stdout, stderr=stderr, timeout=120)
        row = dict(command=command, exit_code=result.returncode)
        write(out / (name + '.json'), row)
        assert result.returncode == 0
        tests = json.loads((out / name / 'RESULT.json').read_bytes())
        assert tests['tests'] == 16 and tests['failures'] == 0 and tests['errors'] == 0 and tests['skips'] == skipped and tests['successful'] is True
        print(name + ':16 policy cases; expected skips=' + str(skipped), flush=True)
        return dict(name=name, tests=tests, process=row)

    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
        runs = list(pool.map(check, commands))
    assert all(pin(ROOT / name) == value for name, value in sources.items())
    write(out / 'COMPLETED.json', dict(status='passed-source-pinned-Windows-and-Linux-Salmon-installer-policy-controls', runs=runs, source_pins=sources,
          scope='Authorization, evidence preservation, finalization/cancellation and archive-consumption policies. '
                'Linux executes two real generic process ownership/cleanup cases. Mock policy cases do not quantify data or admit an original runtime.',
          scientific_runtime_admitted=False, user_analysis_admitted=False))


if __name__ == '__main__':
    main()
