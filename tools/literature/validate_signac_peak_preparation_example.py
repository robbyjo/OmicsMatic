# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Run the copyable native fragment-preparation example on three JVMs."""
import argparse
import os
import subprocess
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save
from validate_deseq2_inference import JAVA21, JAVA25, linux


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--out', type=Path, required=True)
    out = p.parse_args().out.resolve()
    base = ROOT / 'build/literature-review/signac-runtime'
    assert out.is_relative_to(base) and not out.exists()
    out.mkdir()
    jar = base / 'peaks-preparation-development/omicsmatic-0.3.7-signac-peak-preparation-development.jar'
    example = ROOT / 'examples/signac-peak-preparation'
    artifact = pin(jar)
    sources = {f.relative_to(ROOT).as_posix(): pin(f) for f in [Path(__file__), *example.iterdir()] if f.is_file()}
    classes = out / 'classes'
    classes.mkdir()

    def command(label, argv, expected=0):
        argv = list(map(str, argv))
        with (out / (label + '.stdout')).open('xb') as stdout, (out / (label + '.stderr')).open('xb') as stderr:
            r = subprocess.run(argv, cwd=ROOT, stdin=subprocess.DEVNULL, stdout=stdout, stderr=stderr, timeout=120)
        save(out / (label + '.process.json'), dict(command=argv, exit_code=r.returncode, expected_exit_code=expected))
        assert r.returncode == expected, label

    try:
        command('compile', [Path(JAVA21).with_name('javac.exe'), '--release', '17', '-encoding', 'UTF-8',
                '-Xlint:all', '-Werror', '-cp', jar, '-d', classes, example / 'PeakPreparationExample.java'])
        runs = []
        for platform in ['windows-java21', 'windows-java25', 'linux-java21']:
            destination = out / (platform + '-output')
            entries = [jar, classes]
            if platform == 'linux-java21':
                argv = ['wsl.exe', '-d', 'JLinAlg-Omics-SAIGE-20261026', '--', '/usr/bin/java', '-cp',
                        ':'.join(map(linux, entries)), 'PeakPreparationExample',
                        linux(example / 'fragments.tsv'), linux(destination)]
            else:
                argv = [JAVA21 if platform == 'windows-java21' else JAVA25, '-cp', os.pathsep.join(map(str, entries)),
                        'PeakPreparationExample', example / 'fragments.tsv', destination]
            command(platform, argv)
            expected = [b'chr1\t10\t40\tcell_a\t99\nchr1\t50\t80\tcell_a\t0\n',
                        b'chr1\t20\t60\tcell_b\t1\n', b'']
            assert [p.name for p in sorted(destination.iterdir())] == ['g1.bed', 'g2.bed', 'g3.bed']
            assert all((destination / ('g' + str(i + 1) + '.bed')).read_bytes() == value for i, value in enumerate(expected))
            text = (out / (platform + '.stdout')).read_text(encoding='utf8')
            assert text.splitlines()[:3] == ['group_A\t2\tg1.bed', 'group_B\t1\tg2.bed', 'group_C\t0\tg3.bed']
            assert 'no MACS peak call' in text
            assert 'Required action:' in (out / (platform + '.stderr')).read_text(encoding='utf8')
            before = {p.name: pin(p) for p in destination.iterdir()}
            command(platform + '-existing-output', argv, 1)
            assert before == {p.name: pin(p) for p in destination.iterdir()}
            runs.append(dict(JVM=platform, files=before, existing_output_preserved=True))
        assert pin(jar) == artifact and all(pin(ROOT / n) == value for n, value in sources.items())
        save(out / 'COMPLETED.json', dict(status='passed-three-JVM-native-peak-preparation-example',
                                         source_pins=sources, artifact=artifact, JVMs=runs,
                                         actual_MACS_peak_fits=False, biological_validation=False))
        print('Native peak-preparation example passed on3 JVMs; existing outputs preserved')
    except Exception as e:
        save(out / 'FAILURE.json', dict(status='FAIL2_NATIVE_PEAK_PREPARATION_EXAMPLE', error=str(e)))
        raise


if __name__ == '__main__':
    main()
