#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Original NumPy eigvalsh controls and the full tutorial's PoPS covariance adjustment."""
from pathlib import Path
import argparse, hashlib, importlib.util, json
import numpy as np
import pandas as pd


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--upstream', type=Path, required=True)
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args()
    out = args.out.resolve()
    out.mkdir(parents=True, exist_ok=False)
    assert np.__version__ == '1.19.5' and pd.__version__ == '1.0.5'
    code = args.upstream / 'pops.py'
    before = code.read_bytes()
    assert hashlib.sha256(before).hexdigest() == '35e5af04748d6477e47a8a99a941de086dab39bc829655218a4817242507e3fc'
    spec = importlib.util.spec_from_file_location('original_pops', str(code))
    original = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(original)
    matrices = dict(zero=np.zeros((4, 4)), singleton=np.array([[-.12]]), diagonal=np.diag([-3., -1., 0., 2., 2.]),
                    indefinite=np.array([[1., 1.4], [1.4, 1.]]))
    rng = np.random.RandomState(897)
    for n in [2, 9, 32, 127, 256]:
        a = rng.normal(size=(n, n))
        matrices['symmetric-%d' % n] = (a + a.T) / 2
        b = rng.normal(size=(n, max(1, n // 4)))
        matrices['rank-deficient-%d' % n] = b.dot(b.T)
    for n in [16, 129, 400]:
        matrices['tridiagonal-%d' % n] = np.diag(np.ones(n)*2) + np.diag(np.ones(n-1)*-.9, 1) + np.diag(np.ones(n-1)*-.9, -1)
        matrices['near-singular-%d' % n] = np.ones((n, n)) + np.eye(n)*1e-12
    for scale in [1e-250, 1e250]:
        matrices['scaled-%g' % scale] = matrices['symmetric-32'] * scale
    cases = []
    for name, matrix in matrices.items():
        np.savetxt(out / (name + '.matrix.tsv'), matrix, delimiter='\t', fmt='%.17g')
        values = np.linalg.eigvalsh(matrix)
        np.savetxt(out / (name + '.eigenvalues.tsv'), values, fmt='%.17g')
        cases.append(dict(name=name, dimension=len(matrix)))
    raw_path = args.upstream / 'example/data/magma_scores/PASS_Schizophrenia.genes.raw'
    target_path = args.upstream / 'example/data/magma_scores/PASS_Schizophrenia.genes.out'
    sigmas, metadata = original.munge_magma_covariance_metadata(raw_path)
    y = pd.read_csv(target_path, delim_whitespace=True).ZSTAT.values
    minimum = 0
    for i, sigma in enumerate(sigmas):
        values = np.linalg.eigvalsh(sigma)
        minimum = min(minimum, min(values))
        np.savetxt(out / ('tutorial-block-%02d.eigenvalues.tsv' % i), values, fmt='%.17g')
    variance = np.var(y)
    shift = abs(min(minimum, 0)) + .05 + .9*max(0, variance-1)
    properties = dict(minimum_eigenvalue=minimum, diagonal_shift=shift, target_variance=variance,
                      blocks=len(sigmas), eigenvalues=sum(len(s) for s in sigmas))
    (out / 'tutorial-eigen.properties').write_text(''.join('%s=%s\n' % p for p in properties.items()))
    assert code.read_bytes() == before
    status = dict(status='PASS_ORIGINAL_NUMPY_EIGENVALUES_AND_FULL_POPS_TUTORIAL_COVARIANCE',
                  synthetic_cases=cases, tutorial=properties, source_modified=False,
                  original_parser='pops.munge_magma_covariance_metadata', eigensolver='numpy 1.19.5 numpy.linalg.eigvalsh',
                  pops_sha256=hashlib.sha256(before).hexdigest())
    (out / 'STATUS.json').write_text(json.dumps(status, indent=2)+'\n', encoding='utf-8')
    print(json.dumps(status))


if __name__ == '__main__':
    main()
