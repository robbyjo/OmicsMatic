# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Check native prepared counts, no-holdout warnings and named unsupported skips in the actual JAR example."""
import argparse
import json
import os
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools/literature'))
from validate_mashr_native import JAVA21, linux, pin


def write(path, value):
    path.write_text(json.dumps(value, indent=2) + '\n', encoding='utf-8', newline='\n')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--validation', type=Path, required=True)
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args()
    out = args.out.resolve()
    assert out.is_relative_to((ROOT / 'build/literature-review/meme-runtime').resolve()) and not out.exists()
    out.mkdir(parents=True)
    proof = json.loads(args.validation.read_bytes())
    assert proof['status'] == 'passed-exact-STREME-Fisher-statistical-component'
    artifact = ROOT / proof['artifact']['path']
    assert pin(artifact) == {k: proof['artifact'][k] for k in ['bytes', 'sha256']}
    source = ROOT / 'examples/streme-enrichment/Example.java'
    readme = source.with_name('README.md')
    sources = {p.relative_to(ROOT).as_posix(): pin(p) for p in [source, readme, Path(__file__)]}
    for name in sources:
        target = out / 'source-snapshot' / name
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes((ROOT / name).read_bytes())
    processes = []

    def run(name, command, expected=0):
        command = list(map(str, command))
        with (out / (name + '.stdout')).open('xb') as stdout, (out / (name + '.stderr')).open('xb') as stderr:
            r = subprocess.run(command, cwd=ROOT, stdin=subprocess.DEVNULL, stdout=stdout, stderr=stderr, timeout=120)
        row = dict(name=name, command=command, exit_code=r.returncode, expected_exit=expected)
        write(out / (name + '.json'), row)
        processes.append(row)
        assert r.returncode == expected, row
        return (out / (name + '.stdout')).read_text(encoding='utf-8'), (out / (name + '.stderr')).read_text(encoding='utf-8')

    try:
        classes = out / 'classes'
        classes.mkdir()
        run('compile', [JAVA21 / 'javac.exe', '--release', '17', '-encoding', 'UTF-8', '-Xlint:all', '-Werror', '-cp', artifact, '-d', classes, source])
        base = ROOT / 'build/literature-review/meme-runtime/discovery-original-5.5.4-1/captured'
        xml = base / 'streme-no-holdout/result/streme.xml'
        other = base / 'streme-binomial/result/streme.xml'
        platforms = [('windows-java21', [JAVA21 / 'java.exe'], str, os.pathsep),
                     ('windows-java25', ['C:/Program Files/Common Files/Oracle/Java/javapath/java.exe'], str, os.pathsep),
                     ('linux-java21', ['wsl.exe', '-d', 'JLinAlg-Omics-SAIGE-20261026', '--exec', '/usr/bin/java'], linux, ':')]
        for name, java, convert, sep in platforms:
            command = [*java, '-Xmx256m', '-cp', sep.join(map(convert, [classes, artifact])), 'Example']
            for suffix, tail, code in [('prepared', [], 0), ('no-holdout', ['--xml', '5.5.4', convert(xml)], 0),
                                       ('unsupported', ['--xml', '5.5.4', convert(other)], 3)]:
                stdout, stderr = run(name + '-' + suffix, [*command, *tail], code)
                assert ('WARNING:' in stderr if code == 0 else 'SKIP3' in stderr)
                if suffix == 'no-holdout':
                    assert 'NO_HOLDOUT:' in stderr and 'source sentinels' in stderr and 'holdout-declared=false' in stdout
                elif suffix == 'prepared':
                    assert stdout.count('exact=') == 2 and 'training-selection-score' in stdout
                else:
                    assert 'Required action:' in stderr and not stdout
        assert all(pin(ROOT / name) == wanted for name, wanted in sources.items())
        write(out / 'COMPLETED.json', dict(status='passed-native-STREME-statistical-example-three-JVMs', artifact=proof['artifact'],
              parent_validation=pin(args.validation), source_pins=sources, processes=processes,
              successful_demonstrations=6, named_unsupported_skips=3, discovery_analysis_admitted=False))
        print('Six native demonstrations and three named SKIP3 examples passed on three JVMs', flush=True)
    except Exception as failure:
        write(out / 'FAILURE.json', dict(status='failed-statistical-example-verification-not-admitted', error=str(failure),
              source_pins=sources, processes=processes, discovery_analysis_admitted=False))
        raise


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable native example verification')
    main()
