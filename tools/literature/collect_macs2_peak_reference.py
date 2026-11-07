# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Real original MACS2 peak calls for the pending typed Signac CallPeaks workflow."""
import argparse
import gzip
import json
import os
import random
import signal
import subprocess
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, required=True)
    parser.add_argument('--versions', nargs='+', choices=['2.2.7.1', '2.2.9', '2.2.9.1'], required=True)
    args = parser.parse_args()
    out = args.out.resolve()
    base = ROOT / 'build/literature-review/signac-runtime'
    assert os.uname().sysname == 'Linux' and out.is_relative_to(base) and not out.exists()
    out.mkdir()
    setup = base / 'peaks-reference-setup-5'
    progress = json.loads((setup / 'BUILD-PROGRESS.json').read_bytes())
    runtimes = {p['version']: p for p in progress}
    assert len(args.versions) == len(set(args.versions)) and set(args.versions) <= set(runtimes)
    inputs = out / 'inputs'
    inputs.mkdir()
    rng = random.Random(731)
    rows = []
    for i in range(3000):
        start = rng.randrange(100, 999000)
        rows.append(('chr1', start, start + rng.randrange(60, 300), 'cell' + str(i % 4 + 1), i % 17 + 1))
    for center, barcodes in [(10000, ['cell1', 'cell2']), (50000, ['cell3', 'cell4']), (100000, ['cell1', 'cell3'])]:
        for i in range(1200):
            start = center + rng.randrange(-75, 76)
            rows.append(('chr1', start, start + rng.randrange(75, 150), barcodes[i % 2], i % 19 + 1))
    rows.sort()
    for name, selected in [('a', {'cell1', 'cell2'}), ('b', {'cell3', 'cell4'})]:
        body = ''.join('\t'.join(map(str, r)) + '\n' for r in rows if r[3] in selected).encode('ascii')
        (inputs / (name + '.tsv')).write_bytes(body)
        (inputs / (name + '.tsv.gz')).write_bytes(gzip.compress(body, mtime=0))
        (inputs / (name + '-support.tsv')).write_text(''.join('\t'.join(map(str, (*r[:4], 100000))) + '\n' for r in rows if r[3] in selected))
    (inputs / 'low.tsv').write_text(''.join('chr1\t' + str(1000 + i * 50000) + '\t' + str(1100 + i * 50000) + '\tcell1\t1\n' for i in range(10)))
    (inputs / 'empty.tsv').write_bytes(b'')
    specifications = [
        ('pooled-BED', ['a.tsv', 'b.tsv'], 'BED', False, []),
        ('pooled-gzip-BED', ['a.tsv.gz', 'b.tsv.gz'], 'BED', False, []),
        ('support-does-not-weight', ['a-support.tsv', 'b-support.tsv'], 'BED', False, []),
        ('pooled-BEDPE', ['a.tsv', 'b.tsv'], 'BEDPE', False, []),
        ('pooled-broad', ['a.tsv', 'b.tsv'], 'BED', True, []),
        ('group-A', ['a.tsv'], 'BED', False, []),
        ('group-B', ['b.tsv'], 'BED', False, []),
        ('no-significant-peaks', ['low.tsv'], 'BED', False, ['-q', '1e-20']),
        ('empty-input', ['empty.tsv'], 'BED', False, [])]
    env = {k: v for k, v in os.environ.items() if not k.startswith(('PYTHON', 'R_LIBS', 'LC_'))}
    env.update(PYTHONNOUSERSITE='1', PYTHONDONTWRITEBYTECODE='1', LANG='C', LC_ALL='C', OMP_NUM_THREADS='1', OPENBLAS_NUM_THREADS='1')
    sources = {p.relative_to(ROOT).as_posix(): pin(p) for p in [Path(__file__), setup / 'BUILD-PROGRESS.json']}
    # The installer can append later editions. Pin selected entries, not its live progress file.
    sources.pop((setup / 'BUILD-PROGRESS.json').relative_to(ROOT).as_posix())
    reports = []
    try:
        for version in args.versions:
            home = ROOT / runtimes[version]['home']
            package = home / 'lib/python3.9/site-packages/MACS2'
            before = {p.relative_to(package).as_posix(): pin(p) for p in package.rglob('*') if p.is_file()}
            folder = out / version
            folder.mkdir()
            cases = []
            for name, files, fmt, broad, additional in specifications:
                destination = folder / name
                destination.mkdir()
                argv = [str(home / 'bin/macs2'), 'callpeak', '-t', *[str(inputs / n) for n in files],
                        '-g', '1000000', '-f', fmt]
                if broad:
                    argv += ['--broad']
                if fmt == 'BED':
                    argv += ['--nomodel', '--extsize', '200', '--shift', '-100']
                argv += ['-n', 'fixture', '--outdir', str(destination), *additional]
                with (destination / 'stdout').open('xb') as stdout, (destination / 'stderr').open('xb') as stderr:
                    process = subprocess.Popen(argv, cwd=ROOT, env=env, stdin=subprocess.DEVNULL,
                                               stdout=stdout, stderr=stderr, start_new_session=True)
                    try:
                        process.wait(timeout=120)
                    except subprocess.TimeoutExpired:
                        os.killpg(process.pid, signal.SIGKILL)
                        process.wait(timeout=10)
                        raise RuntimeError('Original MACS control timed out: ' + version + '/' + name)
                save(destination / 'PROCESS.json', dict(command=argv, exit_code=process.returncode))
                if name == 'empty-input':
                    assert process.returncode != 0, 'Original empty-input behavior changed; inspect rather than repair'
                    count = None
                else:
                    assert process.returncode == 0, version + '/' + name + ' original failed'
                    peaks = destination / ('fixture_peaks.' + ('broadPeak' if broad else 'narrowPeak'))
                    values = peaks.read_text().splitlines()
                    assert all(len(line.split('\t')) == (9 if broad else 10) for line in values)
                    count = len(values)
                    assert (count == 0) == (name == 'no-significant-peaks'), (name, count)
                cases.append(dict(name=name, format=fmt, broad=broad, exit_code=process.returncode,
                                  peak_rows=count, files={p.name: pin(p) for p in destination.iterdir() if p.is_file()}))
                print(version + '/' + name + ' retained original exit=' + str(process.returncode) + ', peaks=' + str(count), flush=True)
            for name in ['pooled-gzip-BED', 'support-does-not-weight']:
                assert (folder / 'pooled-BED/fixture_peaks.narrowPeak').read_bytes() == (folder / name / 'fixture_peaks.narrowPeak').read_bytes()
            assert before == {p.relative_to(package).as_posix(): pin(p) for p in package.rglob('*') if p.is_file()}
            reports.append(dict(version=version, runtime=runtimes[version], package_unchanged=before, cases=cases))
        assert all(pin(ROOT / n) == value for n, value in sources.items())
        save(out / 'COMPLETED.json', dict(status='passed-real-original-MACS2-peak-reference-calls-not-Java-workflow-admission',
                                         source_pins=sources, inputs={p.name: pin(p) for p in inputs.iterdir()}, runtimes=reports,
                                         actual_calls=9 * len(reports), expected_empty_input_failures=len(reports),
                                         valid_empty_peak_results=len(reports),
                                         input_records=len(rows), support_ignored_and_gzip_equivalence=True,
                                         Java_CallPeaks_adapter_admitted=False, biological_validation=False,
                                         historical_whole_environment_reproduced=False))
        print('Completed original MACS peak controls for ' + ', '.join(args.versions), flush=True)
    except Exception as error:
        save(out / 'FAILURE.json', dict(status='FAIL2_ORIGINAL_MACS_PEAK_CONTROLS', error=str(error),
                                       required_action='Preserve original logs/results and inspect the named source failure. No Java/user result is admitted. Do not substitute another peak caller.'))
        raise


if __name__ == '__main__':
    main()
