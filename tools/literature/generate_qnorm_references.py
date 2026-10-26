"""Freeze actual qnorm0.8.0 finite float64/axis1 output; no Java formula."""
import argparse
import hashlib
import importlib.metadata
import json
from pathlib import Path
import sys

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--package-home', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    sys.path.insert(0, str(args.package_home.resolve()))
    import numpy as np
    import numba
    import qnorm
    assert importlib.metadata.version('qnorm') == '0.8.0'
    assert Path(qnorm.__file__).resolve().is_relative_to(args.package_home.resolve())
    rng = np.random.default_rng(2984701)
    cases = [
        (np.array([[5,4,3],[2,1,4],[3,4,6],[4,2,8]], dtype=np.float64), None),
        (np.array([[1,2,3],[1,2,3],[1,2,3]], dtype=np.float64), None),
        (np.array([[3,1],[1,3],[3,2],[2,3]], dtype=np.float64), np.array([8,2,6,4], dtype=np.float64)),
        (np.array([[7.5]], dtype=np.float64), None),
        (np.array([[-3],[0],[-3],[2]], dtype=np.float64), np.array([11,3,7,5], dtype=np.float64)),
        (np.array([[0.0,-0.0],[-0.0,0.0],[1,2]], dtype=np.float64), None),
    ]
    for case in range(494):
        rows, columns = int(rng.integers(1, 33)), int(rng.integers(1, 15))
        matrix = rng.normal(0, 4, (rows, columns))
        if case % 3 == 0:
            matrix = rng.integers(-5, 8, (rows, columns)).astype(np.float64)
        elif case % 3 == 1:
            matrix = np.round(matrix, 1)
        if case % 7 == 0:
            matrix[:, 0] = matrix[0, 0]
        target = rng.permutation(rng.normal(0, 8, rows)) if case % 2 else None
        cases.append((matrix, target))
    assert not args.output.exists(), args.output
    args.output.mkdir(parents=True)
    flattened = lambda matrix: ','.join(format(float(value), '.17g') for value in matrix.ravel())
    lines = ['case\trows\tcolumns\tinput_row_major\ttarget\texpected_row_major']
    positions = 0
    for number, (matrix, target) in enumerate(cases):
        original = matrix.copy()
        original_target = None if target is None else target.copy()
        result = qnorm.quantile_normalize(matrix, axis=1, target=target, ncpus=1)
        np.testing.assert_array_equal(matrix, original)
        if target is not None:
            np.testing.assert_array_equal(target, original_target)
        assert result.shape == matrix.shape and np.isfinite(result).all()
        positions += result.size
        lines.append('\t'.join([str(number), str(matrix.shape[0]), str(matrix.shape[1]), flattened(matrix), 'default' if target is None else flattened(target), flattened(result)]))
    reference = args.output / 'column-reference.tsv'
    reference.write_text('\n'.join(lines)+'\n', encoding='utf-8')
    function = args.package_home / 'qnorm/quantile_normalize.py'
    record = dict(original_version='0.8.0', original_wheel_sha256='bed17f5bf76df9be75d1315e31f6874ffbd360195caa230f1d5c543fde66bc3d',
                  original_function_sha256=hashlib.sha256(function.read_bytes()).hexdigest(),
                  generator_sha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
                  reference_sha256=hashlib.sha256(reference.read_bytes()).hexdigest(), cases=len(cases), positions=positions,
                  seed=2984701, numpy=np.__version__, numba=numba.__version__, python=sys.version,
                  invocation='Actual original qnorm.quantile_normalize ndarray float64 axis=1 ncpus=1; default or explicit target',
                  scope='Finite float64 column normalization and tie/target behavior only. No float32/int32 rounding, ndarray axis0 transpose quirk, pandas/incremental/multiprocessing or full-paper parity. Nonfinite upstream inputs are not executed.')
    (args.output / 'reference-provenance.json').write_text(json.dumps(record, indent=2)+'\n', encoding='utf-8')
    print(json.dumps(record))

if __name__ == '__main__':
    main()
