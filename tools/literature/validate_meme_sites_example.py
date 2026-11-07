# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Check portable source-bound MEME site example and preserved existing-output refusals."""
import argparse
import json
import os
from pathlib import Path
import subprocess
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools/literature'))
from validate_mashr_native import JAVA21, linux, pin


def write(path, value):
    path.write_text(json.dumps(value, indent=2) + '\n', encoding='utf-8', newline='\n')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--validation', type=Path, required=True); parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args(); out = args.out.resolve()
    assert out.is_relative_to((ROOT / 'build/literature-review/meme-runtime').resolve()) and not out.exists(); out.mkdir(parents=True)
    proof = json.loads(args.validation.read_bytes()); assert proof['status'] == 'passed-exact-source-MEME-discovery-site-and-scoring-components'
    jar = ROOT / proof['artifact']['path']; assert pin(jar) == {k: proof['artifact'][k] for k in ['bytes', 'sha256']}
    source = ROOT / 'examples/meme-discovery-sites/Example.java'; readme = source.with_name('README.md')
    sources = {p.relative_to(ROOT).as_posix(): pin(p) for p in [source, readme, Path(__file__)]}
    for name in sources:
        target = out / 'source-snapshot' / name; target.parent.mkdir(parents=True, exist_ok=True); target.write_bytes((ROOT / name).read_bytes())
    records = []

    def run(name, command, expected=0):
        command = list(map(str, command))
        with (out / (name + '.stdout')).open('xb') as stdout, (out / (name + '.stderr')).open('xb') as stderr:
            r = subprocess.run(command, cwd=ROOT, stdin=subprocess.DEVNULL, stdout=stdout, stderr=stderr, timeout=120)
        row = dict(name=name, command=command, exit_code=r.returncode, expected_exit=expected); write(out / (name + '.json'), row); records.append(row)
        assert r.returncode == expected, row
        return (out / (name + '.stdout')).read_text(encoding='utf-8'), (out / (name + '.stderr')).read_text(encoding='utf-8')

    try:
        classes = out / 'classes'; classes.mkdir()
        run('compile', [JAVA21 / 'javac.exe', '--release', '17', '-encoding', 'UTF-8', '-Xlint:all', '-Werror', '-cp', jar, '-d', classes, source])
        assert {p.relative_to(classes).as_posix() for p in classes.rglob('*.class')} == {'Example.class'}
        with zipfile.ZipFile(ROOT / 'src/test/resources/org/jlinalg/sequence/meme-discovery-sites/original-site-contracts.zip') as z:
            data = json.loads(z.read('EXPECTED.json')); family = next(r for r in data['families'] if r['name'] == 'sites-original-5.5.9-1/edge-both')
        sites = sum(m['sites'] for m in family['motifs']); hits = sum(len(s['selected']) for s in family['scans'])
        platforms = [('windows-java21', [JAVA21 / 'java.exe'], str, os.pathsep),
                     ('windows-java25', ['C:/Program Files/Common Files/Oracle/Java/javapath/java.exe'], str, os.pathsep),
                     ('linux-java21', ['wsl.exe', '-d', 'JLinAlg-Omics-SAIGE-20261026', '--cd', linux(ROOT), '--exec', '/usr/bin/java'], linux, ':')]
        for name, java, convert, sep in platforms:
            target = out / (name + '-output'); command = [*java, '-Xmx256m', '-cp', sep.join(map(convert, [classes, jar])), 'Example', convert(target)]
            stdout, stderr = run(name, command)
            assert f'contributing-sites={sites}; sequence-diagrams=60; selected-hits={hits}' in stdout
            assert 'COMPONENT_ONLY:' in stderr and 'REQUIRED ACTION:' in stderr and 'motif significance' in stderr
            before = {p.name: pin(p) for p in target.iterdir()}; assert set(before) == {'original-meme.xml', 'original-input.fa'}
            stdout, stderr = run(name + '-existing-output', command, 2)
            assert not stdout and 'FAIL2' in stderr and 'Required action:' in stderr and 'NEW' in stderr
            assert {p.name: pin(p) for p in target.iterdir()} == before
        assert all(pin(ROOT / n) == v for n, v in sources.items()) and pin(jar) == {k: proof['artifact'][k] for k in ['bytes', 'sha256']}
        write(out / 'COMPLETED.json', dict(status='passed-portable-MEME-site-example-three-JVMs', artifact=proof['artifact'], parent_validation=pin(args.validation),
              source_pins=sources, processes=records, successful_demonstrations=3, existing_output_refusals=3,
              original_contributing_sites=sites, original_scan_hits=hits, original_sequence_diagrams=60, discovery_analysis_admitted=False))
        print('Three portable native demonstrations and three preserved existing-output refusals passed', flush=True)
    except Exception as failure:
        write(out / 'FAILURE.json', dict(status='failed-MEME-site-example-not-admitted', error=str(failure), source_pins=sources, processes=records))
        raise


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable portable example checks')
    main()
