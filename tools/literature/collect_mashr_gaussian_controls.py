#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Run the installed original mashr on complete synthetic fixed-mixture controls.

This is a private development reference collector, not a user-analysis adapter.
It neither downloads nor installs dependencies and refuses to reuse an output.
"""
import argparse
import hashlib
import json
import math
import os
from pathlib import Path
import random
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
BASE = ROOT / 'build/literature-review/mashr-runtime'
RSCRIPT = Path('C:/Program Files/R/R-4.6.1/bin/Rscript.exe')


def pin(path):
    raw = path.read_bytes()
    return dict(bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest())


def matrix(path, rows):
    path.write_text(''.join('\t'.join(format(x, '.17g') for x in row) + '\n' for row in rows), encoding='utf-8')


def identity(n):
    return [[float(i == j) for j in range(n)] for i in range(n)]


def outer(v):
    return [[x * y for y in v] for x in v]


def canonical(n):
    result = [identity(n)]
    for c in range(n):
        result.append(outer([float(c == j) for j in range(n)]))
    result.append(outer([1.] * n))
    for correlation in (.25, .5, .75):
        result.append([[1. if i == j else correlation for j in range(n)] for i in range(n)])
    return result


def cases():
    rng = random.Random(702791)
    output = []
    def add(name, n, priors, proportions, bhat, shat, residual=None, alpha=0., threshold=1e-10):
        output.append(dict(name=name, n=n, alpha=alpha, threshold=threshold, priors=priors,
                           proportions=proportions, bhat=bhat, shat=shat, residual=residual or identity(n)))
    add('shared-analytic', 2, [outer([0., 0.]), outer([1., 1.])], [.2, .8],
        [[1., 2.], [-1., 0.], [0., 0.], [-2., -4.]], [[1., 1.]] * 4)
    for n in (2, 3, 5, 9):
        priors = [outer([0.] * n)] + canonical(n)
        weights = [1. / len(priors)] * len(priors)
        effects = [[rng.gauss(0, 2) for _ in range(n)] for _ in range(11)]
        errors = [[math.exp(rng.uniform(-1.5, 1.5)) for _ in range(n)] for _ in effects]
        v = [[1. if i == j else .35 ** abs(i-j) for j in range(n)] for i in range(n)]
        for alpha in (0., .5, 1., -1., 2.):
            add('canonical-' + str(n) + '-alpha-' + str(alpha), n, priors, weights, effects, errors, v, alpha)
        add('common-errors-' + str(n), n, priors, weights, effects, [[1.3] * n] * len(effects), v)
        direction = [rng.uniform(-1, 1) for _ in range(n)]
        gram = [[rng.gauss(0, 1) for _ in range(n)] for _ in range(n)]
        spd = [[sum(gram[i][c] * gram[j][c] for c in range(n)) + (.3 if i == j else 0.) for j in range(n)] for i in range(n)]
        add('noncanonical-' + str(n), n, [outer([0.] * n), outer(direction), spd], [.25, .3, .45], effects, errors, v)
    for n in (2, 3, 5):
        effects = [[1.2] + [0.] * (n-1), [-2.] + [0.] * (n-1)]
        errors = [[.4] + [1e10] * (n-1)] * 2
        v = [[1. if i == j else .5 for j in range(n)] for i in range(n)]
        add('explicit-large-SE-' + str(n), n, [outer([0.] * n), identity(n), outer([1.] * n)], [.1, .3, .6], effects, errors, v)
    for n in (1, 2, 3):
        b = [[-2.] * n, [0.] * n, [3.] * n]
        se = [[1.] * n] * 3
        add('null-only-' + str(n), n, [outer([0.] * n)], [1.], b, se)
        add('one-normal-' + str(n), n, [identity(n)], [1.], b, se)
    for value, suffix in [(0., 'zero'), (1e-10, 'equal'), (math.nextafter(1e-10, math.inf), 'above'), (math.nextafter(1e-10, 0.), 'below')]:
        add('prior-threshold-' + suffix, 2, [outer([0., 0.]), identity(2), outer([1., -1.])],
            [.7, value, .3-value], [[1., -2.], [0., 0.]], [[.5, 1.], [.5, 1.]])
    # These diagnostic cases exercise numerical cancellation in the original
    # implementation. They are retained even if an algorithm fails or disagrees.
    add('boundary-small-SE', 2, [identity(2), outer([1., 1.])], [.5, .5], [[1., 2.], [-1., -2.]], [[1e-6, 2e-6]] * 2)
    return output


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', required=True, type=Path)
    parser.add_argument('--runtime', required=True, type=Path)
    args = parser.parse_args()
    out, runtime = args.out.resolve(), args.runtime.resolve()
    if not out.is_relative_to(BASE.resolve()) or out == BASE.resolve() or out.exists():
        raise ValueError('Choose a NEW output directory under build/literature-review/mashr-runtime')
    completion = json.loads((runtime / 'COMPLETED.json').read_text(encoding='utf-8'))
    if completion['status'] != 'COMPLETED_PRIVATE_REFERENCE_SETUP_ONLY':
        raise ValueError('Require an explicitly completed private reference setup')
    if pin(RSCRIPT)['sha256'] != 'd829bcf7e9fa1d7e3e828c565c3cdbb1ed416f551f4fa6fd4dfcdf231e33e5e8':
        raise ValueError('Rscript differs from the inspected R4.6.1 reference host')
    out.mkdir(parents=True)
    try:
        for name in ('downloaded-packages.json', 'selected-package-index.json', 'installed-packages.tsv', 'session-info.txt', 'COMPLETED.json'):
            shutil.copyfile(runtime / name, out / ('reference-runtime-' + name))
        source = ROOT / 'tools/literature/mashr-original-controls.R'
        shutil.copyfile(source, out / 'original-controls.R')
        shutil.copyfile(Path(__file__), out / 'collector.py')
        catalog = cases()
        with (out / 'cases.tsv').open('w', encoding='utf-8', newline='\n') as table:
            table.write('case\tconditions\trows\tcomponents\talpha\tthreshold\n')
            for case in catalog:
                directory = out / case['name']; directory.mkdir()
                table.write('\t'.join(map(str, [case['name'], case['n'], len(case['bhat']), len(case['priors']), case['alpha'], case['threshold']])) + '\n')
                for key in ('bhat', 'shat', 'residual'):
                    matrix(directory / (key + '.tsv'), case[key])
                matrix(directory / 'proportions.tsv', [case['proportions']])
                for i, covariance in enumerate(case['priors']):
                    matrix(directory / ('prior-' + str(i) + '.tsv'), covariance)
        command = [str(RSCRIPT), '--vanilla', str(out / 'original-controls.R'), str(runtime / 'library'), str(out)]
        environment = dict(os.environ, LANG='C', LC_ALL='C', R_LIBS_USER=str(runtime / 'library'), R_LIBS_SITE='',
                           OMP_NUM_THREADS='1', OPENBLAS_NUM_THREADS='1', MKL_NUM_THREADS='1')
        (out / 'command.json').write_text(json.dumps(dict(argv=command, cwd=str(out), timeout_seconds=600,
                  environment_overrides={k:environment[k] for k in ('LANG', 'LC_ALL', 'R_LIBS_USER', 'R_LIBS_SITE', 'OMP_NUM_THREADS', 'OPENBLAS_NUM_THREADS', 'MKL_NUM_THREADS')}), indent=2) + '\n', encoding='utf-8')
        with (out / 'process.stdout').open('wb') as stdout, (out / 'process.stderr').open('wb') as stderr:
            process = subprocess.run(command, cwd=out, env=environment, stdin=subprocess.DEVNULL, stdout=stdout, stderr=stderr, timeout=600, check=False)
        (out / 'process.json').write_text(json.dumps(dict(exit_code=process.returncode), indent=2) + '\n')
        if process.returncode:
            raise RuntimeError('Original R control collector exited ' + str(process.returncode) + '; inspect process.stderr')
        statuses = []
        for case in catalog:
            for algorithm in ('R', 'Rcpp'):
                status = (out / case['name'] / algorithm / 'status.txt').read_text().strip()
                statuses.append(dict(case=case['name'], algorithm=algorithm, status=status))
        record = dict(schema_version=1, status='captured-original-gaussian-controls', cases=len(catalog), original_algorithms=['R', 'Rcpp'],
                      rows=sum(len(c['bhat']) for c in catalog), outcomes=statuses, setup_only=False, user_analysis_admitted=False,
                      source_to_binary_reproduction_verified=False, biological_accuracy_certified=False,
                      source_pins={str(p.relative_to(ROOT)).replace('\\', '/'):pin(p) for p in [Path(__file__), source]},
                      files={p.relative_to(out).as_posix():pin(p) for p in sorted(out.rglob('*')) if p.is_file()})
        (out / 'record.json').write_text(json.dumps(record, indent=2) + '\n', encoding='utf-8')
        failed = [s for s in statuses if s['status'] != 'COMPLETED']
        (out / 'WARNINGS.md').write_text('# Private original mashr controls\n\n'
            'These are synthetic development references, not a public user-analysis workflow or biological certificate. '
            'Both original algorithms and any numerical failures remain recorded.\n\n'
            'Required action: inspect per-case status, warnings and errors before comparing or admitting results; '
            'do not discard a failed algorithm or count this foundation as a complete mashr integration.\n', encoding='utf-8')
        print(json.dumps(dict(status=record['status'], cases=len(catalog), rows=record['rows'], failed=failed), indent=2))
        return 0
    except Exception as error:
        (out / 'FAILURE.json').write_text(json.dumps(dict(status='FAIL2_PRIVATE_REFERENCE_COLLECTION', message=str(error)), indent=2) + '\n')
        (out / 'WARNINGS.md').write_text('# FAIL2: original controls incomplete\n\n' + str(error) + '\n\nRequired action: preserve this attempt and inspect the process evidence. Correct the failure and use a NEW output directory. No result is admitted.\n', encoding='utf-8')
        raise


if __name__ == '__main__':
    try:
        sys.exit(main())
    except Exception as failure:
        print('MASHR CONTROLS FAIL2: ' + str(failure), file=sys.stderr)
        sys.exit(2)
