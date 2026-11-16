#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Generate finite prepared-matrix controls using the unmodified original PoPS CLI."""
from pathlib import Path
import argparse, hashlib, json, os, subprocess, sys, time, zipfile
import numpy as np
import pandas as pd
import scipy
from scipy.sparse import csr_matrix, save_npz
import sklearn

COMMIT = '76eb86cba10254490003c8f4dc7ff5ce492d3667'
POPS_SHA256 = '35e5af04748d6477e47a8a99a941de086dab39bc829655218a4817242507e3fc'


def sha(data):
    return hashlib.sha256(data).hexdigest()


def write_json(path, value):
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2, allow_nan=False) + '\n', encoding='utf-8')


def numbers(path, values):
    np.savetxt(str(path), np.asarray(values), delimiter='\t', fmt='%.17g')


def fixture(base, name, *, n=72, p=11, covariance=None, magma=False, low_variance=False,
            covariates=False, different_masks=False, method='ridge', cutoff=.05, maximum=None,
            subset=None, controls=None, forward=None, duplicate=False, constant=False, expect_failure=False):
    out = base / name
    out.mkdir()
    rng = np.random.RandomState(1909)
    x = rng.normal(size=(n, p))
    x[:, -1] = 1
    if duplicate:
        x[:, -2] = x[:, 0]
    if constant:
        x[:, :] = 1
    y = 1.3 * x[:, 0] - .7 * x[:, 1] + .25 * x[:, 2] + rng.normal(size=n) * .65
    y = y * (.12 if low_variance else 1)
    ids = np.array(['SYNTHETIC_G%04d' % i for i in range(n)])
    labels = np.repeat(['1', '2', '6'], n // 3)
    assert len(labels) == n
    positions = np.array([1000000 + 1000 * (i % (n // 3)) if c != '6'
                          else 19000000 + 1000000 * (i % (n // 3)) for i, c in enumerate(labels)])
    annotation = pd.DataFrame(dict(ENSGID=ids, CHR=labels, TSS=positions))
    annotation.to_csv(out / 'annotation.tsv', sep='\t', index=False)
    names = np.array(['F%03d' % i for i in range(p)])
    np.save(str(out / 'features.mat.0.npy'), x)
    np.savetxt(str(out / 'features.rows.txt'), ids, fmt='%s')
    np.savetxt(str(out / 'features.cols.0.txt'), names, fmt='%s')
    numbers(out / 'features.tsv', x)
    numbers(out / 'target.tsv', y)
    error_cov = np.eye(n)
    if covariance is not None or magma:
        for c in ['1', '2', '6']:
            inds = np.where(labels == c)[0]
            error_cov[np.ix_(inds, inds)] = .22 ** np.abs(np.arange(len(inds))[:, None] - np.arange(len(inds))[None, :])
        if covariance == 'indefinite':
            error_cov[0, 1] = error_cov[1, 0] = 1.4
    flags = ['--gene_annot_path', str(out / 'annotation.tsv'), '--feature_mat_prefix', str(out / 'features'),
             '--num_feature_chunks', '1', '--method', method, '--out_prefix', str(out / 'original'), '--save_matrix_files', '--verbose']
    if magma:
        nsnps = rng.uniform(100, 300, n)
        nparam = nsnps * rng.uniform(.2, .85, n)
        mac = rng.uniform(500, 3000, n)
        meta = np.column_stack([nsnps, nparam, mac])
        numbers(out / 'magma-metadata.tsv', meta)
        pd.DataFrame(dict(GENE=ids, ZSTAT=y)).to_csv(out / 'magma.genes.out', sep=' ', index=False)
        with (out / 'magma.genes.raw').open('w') as f:
            f.write('# SYNTHETIC PoPS parser control; not original MAGMA output\n# GENE CHR START END NSNPS NPARAM UNUSED MAC UNUSED CORRELATIONS\n')
            within = 0
            for i in range(n):
                if i == 0 or labels[i] != labels[i-1]:
                    within = 0
                fields = [ids[i], labels[i], '0', '1', '%.17g' % nsnps[i], '%.17g' % nparam[i], '0', '%.17g' % mac[i], '0']
                fields += ['%.17g' % v for v in error_cov[i, i-within:i]]
                f.write(' '.join(fields) + '\n')
                within += 1
        flags += ['--magma_prefix', str(out / 'magma')]
    else:
        pd.DataFrame(dict(ENSGID=ids, Score=y)).to_csv(out / 'scores.tsv', sep='\t', index=False)
        flags += ['--y_path', str(out / 'scores.tsv')]
        if covariates:
            cov = np.column_stack([rng.normal(size=n), rng.uniform(-1, 1, n)])
            frame = pd.DataFrame(cov, columns=['C1', 'C2'])
            frame.insert(0, 'ENSGID', ids)
            frame.to_csv(out / 'score-covariates.tsv', sep='\t', index=False)
            numbers(out / 'covariates.tsv', cov)
            flags += ['--y_covariates_path', str(out / 'score-covariates.tsv')]
        if covariance is not None:
            save_npz(str(out / 'error-covariance.npz'), csr_matrix(error_cov))
            flags += ['--y_error_cov_path', str(out / 'error-covariance.npz')]
    if covariance is not None or magma:
        numbers(out / 'covariance.tsv', error_cov)
    # The original CLI accepts a numeric cutoff. Some controls explicitly use
    # 1.01 to retain every finite marginal p-value; this is not an author setting.
    assert cutoff is not None
    flags += ['--feature_selection_p_cutoff', str(cutoff)]
    if maximum is not None:
        flags += ['--feature_selection_max_num', str(maximum)]
    if subset is not None:
        np.savetxt(str(out / 'subset.txt'), names[subset], fmt='%s')
        flags += ['--subset_features_path', str(out / 'subset.txt')]
    if controls is not None:
        np.savetxt(str(out / 'controls.txt'), names[controls], fmt='%s')
        flags += ['--control_features_path', str(out / 'controls.txt')]
    if forward is not None:
        flags += ['--feature_selection_fss_num_features', str(forward)]
    projection_chrs, selection_chrs, training_chrs = ['1', '2', '6'], ['1', '2', '6'], ['1', '2', '6']
    if different_masks:
        projection_chrs, selection_chrs, training_chrs = ['1', '6'], ['1', '2'], ['2', '6']
        flags += ['--project_out_covariates_chromosomes'] + projection_chrs
        flags += ['--feature_selection_chromosomes'] + selection_chrs
        flags += ['--training_chromosomes'] + training_chrs
    mask_rows = []
    for i in range(n):
        hla = labels[i] == '6' and 20000000 <= positions[i] <= 40000000
        mask_rows.append([int(labels[i] in chrs and not hla) for chrs in [projection_chrs, selection_chrs, training_chrs]])
    numbers(out / 'masks.tsv', mask_rows)
    props = dict(method=method, cutoff=cutoff, maximum='none' if maximum is None else maximum,
                 subset='none' if subset is None else ','.join(map(str, subset)),
                 controls='none' if controls is None else ','.join(map(str, controls)),
                 forward='none' if forward is None else forward,
                 magma=str(magma).lower(), covariance=str(covariance is not None or magma).lower(),
                 expect_failure=str(expect_failure).lower())
    (out / 'params.properties').write_text(''.join('%s=%s\n' % item for item in props.items()))
    return out, flags, expect_failure


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--upstream', type=Path, required=True)
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args()
    upstream, out = args.upstream.resolve(), args.out.resolve()
    out.mkdir(parents=True, exist_ok=False)
    assert sys.version_info[:2] == (3, 8)
    versions = dict(numpy=np.__version__, scipy=scipy.__version__, pandas=pd.__version__, sklearn=sklearn.__version__)
    assert versions == dict(numpy='1.19.5', scipy='1.5.2', pandas='1.0.5', sklearn='0.24.2'), versions
    source_bytes = (upstream / 'pops.py').read_bytes()
    assert sha(source_bytes) == POPS_SHA256, 'Unqualified original PoPS source'
    assert (upstream / 'LICENSE').is_file()
    write_json(out / 'SOURCE.json', dict(commit=COMMIT, pops_sha256=sha(source_bytes), versions=versions,
                                         python=sys.version, python_executable_sha256=sha(Path(sys.executable).read_bytes())))
    cases = [
        ('ridge-plain', {}),
        ('ridge-custom-gls', dict(covariance='positive', covariates=True, different_masks=True)),
        ('ridge-magma', dict(magma=True, different_masks=True)),
        ('ridge-magma-indefinite', dict(magma=True, covariance='indefinite')),
        ('ridge-magma-small-variance', dict(magma=True, low_variance=True)),
        ('ridge-wide', dict(n=36, p=55, cutoff=1.01)),
        ('ols-duplicate', dict(method='linreg', duplicate=True, cutoff=1.01)),
        ('ridge-controls-subset', dict(cutoff=0, maximum=0, subset=[8, 2, 1, 0], controls=[2, 1])),
        ('ridge-top-features', dict(p=41, cutoff=1.01, maximum=4, subset=list(range(40, -1, -1)))),
        ('ridge-fss-overrides', dict(forward=3, cutoff=0, maximum=0, subset=[8], controls=[8])),
        ('ols-fss-gls', dict(method='linreg', forward=2, covariance='positive', different_masks=True)),
        ('failure-no-features', dict(constant=True, expect_failure=True)),
        ('failure-unregularized-covariance', dict(covariance='indefinite', expect_failure=True)),
    ]
    records = []
    for name, options in cases:
        folder, flags, failure = fixture(out, name, **options)
        command = [sys.executable, str(upstream / 'pops.py')] + flags
        started = time.monotonic()
        env = os.environ.copy()
        env.update(OPENBLAS_NUM_THREADS='1', OMP_NUM_THREADS='1', PYTHONHASHSEED='0')
        with (folder / 'original.stdout').open('xb') as so, (folder / 'original.stderr').open('xb') as se:
            process = subprocess.run(command, stdout=so, stderr=se, env=env, timeout=180)
        record = dict(name=name, command=command, exit_code=process.returncode, seconds=round(time.monotonic()-started, 3), expected_failure=failure)
        write_json(folder / 'PROCESS.json', record)
        records.append(record)
        assert (process.returncode != 0) == failure, (name, process.returncode, (folder / 'original.stderr').read_text()[-3000:])
        assert (upstream / 'pops.py').read_bytes() == source_bytes, 'Original source changed'
    # Pandas sorts non-NaNs with NumPy's indirect quicksort, then appends NaNs.
    sort_dir = out / 'sort'
    sort_dir.mkdir()
    rng = np.random.RandomState(1007)
    sorts = []
    for n in [0, 1, 15, 16, 17, 32, 33, 100, 1000, 9000]:
        for pattern in ['tied', 'sorted', 'reversed', 'constant', 'organ-pipe']:
            values = rng.randint(0, 9, n).astype(float)
            if pattern == 'sorted': values.sort()
            if pattern == 'reversed': values = np.sort(values)[::-1]
            if pattern == 'constant': values[:] = 0
            if pattern == 'organ-pipe': values = np.minimum(np.arange(n), np.arange(n)[::-1]).astype(float)
            if n > 10: values[[0, 2, n-1]] = np.nan
            expected = pd.Series(values).sort_values().index.values
            key = '%d-%s' % (n, pattern)
            numbers(sort_dir / (key + '.values'), values)
            np.savetxt(str(sort_dir / (key + '.order')), expected, fmt='%d')
            sorts.append(dict(key=key, size=n))
    write_json(out / 'CASES.json', records)
    write_json(out / 'SORTS.json', sorts)
    status = dict(status='PASS_UNMODIFIED_ORIGINAL_POPS_EXACT_DEPENDENCY_CONTROLS', cases=len(cases),
                  successful_cases=sum(not r['expected_failure'] for r in records), retained_expected_failures=sum(r['expected_failure'] for r in records),
                  sort_cases=len(sorts), new_catalog_credit=0, whole_goal_complete=False)
    write_json(out / 'STATUS.json', status)
    print(json.dumps(status))


if __name__ == '__main__':
    main()
