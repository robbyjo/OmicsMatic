#!/usr/bin/env python3
"""Verify the packaged native genetic PCA API through its runnable synthetic example."""
import argparse
import csv
import hashlib
import json
import math
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def table(path):
    with path.open(encoding='utf-8', newline='') as stream:
        return list(csv.DictReader(stream, delimiter='\t'))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jar', required=True, type=Path)
    parser.add_argument('--javac', required=True, type=Path)
    parser.add_argument('--java', required=True, type=Path, action='append')
    parser.add_argument('--input', type=Path, default=ROOT / 'src/test/resources/org/jlinalg/genetics/genetic-pca')
    parser.add_argument('--example', type=Path, default=ROOT / 'examples/genetic-pca/GeneticPcaExample.java')
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args()
    args.out.mkdir(parents=True, exist_ok=False)
    try:
        import numpy as np
    except ImportError:
        (args.out / 'WARNINGS.md').write_text('# VALIDATION SKIPPED\n\nNumPy is unavailable. Install NumPy in a permitted private Python environment and rerun this verifier to a new directory. No scientific validation was performed.\n')
        (args.out / 'verification.json').write_text(json.dumps(dict(status='skipped', reason='numpy-unavailable')) + '\n')
        print('VALIDATION SKIPPED: NumPy unavailable. Read ' + str(args.out / 'WARNINGS.md'), file=sys.stderr)
        return 3
    input_hashes = {p.name: sha(p) for p in sorted(args.input.iterdir()) if p.is_file()}
    provenance = json.loads((args.input / 'provenance.json').read_text())
    for name, digest in provenance['files_sha256'].items():
        assert input_hashes[name] == digest, name
    assert provenance['samples'] == 125 and provenance['variants'] == 87
    record = dict(status='running', jar_sha256=sha(args.jar), producer_sha256=sha(__file__),
        example_sha256=sha(args.example), input_sha256=input_hashes, numpy=np.__version__,
        scope='Actual compiled public example against the supplied packaged native API; independent NumPy relationship/eigen/weight/projection mathematics. Synthetic hard calls only; no original adapter or whole-PLINK parity claim.',
        runs=[], negative_controls=[])

    def save():
        (args.out / 'verification.json').write_text(json.dumps(record, indent=2) + '\n')

    def run(command, name, timeout=180):
        result = subprocess.run([str(s) for s in command], capture_output=True, timeout=timeout, cwd=ROOT)
        (args.out / (name + '.stdout')).write_bytes(result.stdout)
        (args.out / (name + '.stderr')).write_bytes(result.stderr)
        return result

    save()
    try:
        classes = args.out / 'classes'
        classes.mkdir()
        command = [args.javac, '--release', '17', '-Xlint:all', '-Werror', '-cp', args.jar, '-d', classes, args.example]
        result = run(command, 'compile')
        record['compile_command'] = list(map(str, command))
        record['compile_exit_code'] = result.returncode
        save()
        assert result.returncode == 0, result.stderr.decode(errors='replace')
        samples = table(args.input / 'samples.tsv')
        markers = table(args.input / 'markers.tsv')
        g = np.array([[float(v) if v != 'NA' else np.nan for v in line.split()] for line in (args.input / 'dosages.tsv').read_text().splitlines()])
        observed = np.isfinite(g)
        m, n = g.shape
        components = ['PC' + str(i) for i in range(1, 6)]
        classpath = str(classes.resolve()) + (';' if sys.platform == 'win32' else ':') + str(args.jar.resolve())
        for jvm, java in enumerate(args.java):
            version = run([java, '-version'], 'jvm-' + str(jvm))
            assert version.returncode == 0
            for population in ['all', 'founders']:
                reference = [i for i, row in enumerate(samples) if population == 'all' or row['frequency_founder'] == '1']
                frequency = np.nanmean(g[:, reference], axis=1) / 2
                z = np.where(observed, (g - 2 * frequency[:, None]) / np.sqrt(2 * frequency * (1 - frequency))[:, None], 0)
                for missing in ['mean', 'pairwise']:
                    label = str(jvm) + '-' + population + '-' + missing
                    out = args.out / label
                    command = [java, '-cp', classpath, 'GeneticPcaExample', args.input.resolve(), population, missing, out.resolve()]
                    result = run(command, label)
                    assert result.returncode == 0, (label, result.stderr.decode(errors='replace'))
                    assert (out / 'STATUS.txt').read_text().startswith('PASSED_SYNTHETIC_EXAMPLE')
                    assert (out / 'WARNINGS.md').is_file()
                    expected = z.T @ z / (m if missing == 'mean' else observed.astype(np.int64).T @ observed.astype(np.int64))
                    matrix = np.loadtxt(out / 'relationship.tsv', delimiter='\t')
                    spectrum = np.loadtxt(out / 'spectrum.tsv', delimiter='\t')
                    numeric_bound = 4096 * np.finfo(float).eps * n * max(1, float(np.linalg.norm(expected, 2)))
                    assert np.max(np.abs(matrix - expected)) < numeric_bound
                    eigenvalues = np.linalg.eigvalsh(expected)[::-1]
                    assert spectrum.shape == eigenvalues.shape and np.max(np.abs(spectrum - eigenvalues)) < numeric_bound
                    rows = table(out / 'coordinates.tsv')
                    fitted_rows = [row for row in rows if row['kind'] == 'fitted']
                    projected_rows = [row for row in rows if row['kind'] == 'projected']
                    assert len(rows) == 2 * n and len(fitted_rows) == len(projected_rows) == n
                    for subset in [fitted_rows, projected_rows]:
                        assert [(row['fid'], row['iid']) for row in subset] == [(row['fid'], row['iid']) for row in samples]
                        for i, row in enumerate(subset):
                            assert int(row['observed_variants']) == int(observed[:, i].sum())
                            assert row['status'] == ('COMPLETE' if observed[:, i].all() else 'PARTIALLY_IMPUTED')
                    fitted = np.array([[float(row[c]) for c in components] for row in fitted_rows])
                    projected = np.array([[float(row[c]) for c in components] for row in projected_rows])
                    assert np.max(np.abs(expected @ fitted - fitted * spectrum[:5])) < numeric_bound
                    assert np.max(np.abs(fitted.T @ fitted - np.eye(5))) < numeric_bound
                    for c in range(5):
                        assert fitted[np.argmax(np.abs(fitted[:, c])), c] > 0
                    weight_rows = table(out / 'weights.tsv')
                    assert [(row['id'], row['counted_allele'], row['other_allele']) for row in weight_rows] == [(row['id'], row['counted_allele'], row['other_allele']) for row in markers]
                    assert np.max(np.abs(np.array([float(row['frequency']) for row in weight_rows]) - frequency)) < 1e-15
                    assert [int(row['frequency_observed_samples']) for row in weight_rows] == observed[:, reference].sum(axis=1).tolist()
                    weights = np.array([[float(row[c]) for c in components] for row in weight_rows])
                    expected_weights = (z @ fitted) / m / spectrum[:5]
                    assert np.max(np.abs(weights - expected_weights)) < numeric_bound
                    assert np.max(np.abs(projected - z.T @ expected_weights)) < numeric_bound
                    difference = float(np.max(np.abs(fitted - projected)))
                    if missing == 'mean':
                        assert difference < numeric_bound and spectrum[-1] > -numeric_bound
                    else:
                        assert .0019 < difference < .0022 and -.0042 < spectrum[-1] < -.004
                        assert '**PAIRWISE FIT:**' in (out / 'WARNINGS.md').read_text()
                    record['runs'].append(dict(label=label, java=str(java), command=list(map(str, command)),
                        relationship_entries=n*n, eigenvalues=n, fitted_rows=n, projected_rows=n,
                        projection_weights=m*5, frequency_rows=m, numeric_bound=numeric_bound,
                        minimum_eigenvalue=float(spectrum[-1]), maximum_fitted_projection_difference=difference,
                        files_sha256={f.name: sha(f) for f in sorted(out.iterdir()) if f.is_file()}))
                    save()
                    # Reuse must fail clearly before modifying a completed result.
                    result = run(command, label + '-existing-output')
                    assert result.returncode != 0 and b'Output must be a new directory' in result.stderr
                    assert record['runs'][-1]['files_sha256'] == {f.name: sha(f) for f in sorted(out.iterdir()) if f.is_file()}
                    record['negative_controls'].append(label + '-existing-output-preserved')
                    save()
        assert input_hashes == {p.name: sha(p) for p in sorted(args.input.iterdir()) if p.is_file()}
        record['status'] = 'passed-packaged-native-genetic-PCA-example-and-independent-math'
        record['totals'] = {key: sum(row[key] for row in record['runs']) for key in ['relationship_entries', 'eigenvalues', 'fitted_rows', 'projected_rows', 'projection_weights', 'frequency_rows']}
        save()
        print(json.dumps(dict(status=record['status'], workflows=len(record['runs']), negative_controls=len(record['negative_controls']), totals=record['totals'])))
        return 0
    except Exception as failure:
        record.update(status='failed-retained', error=repr(failure))
        save()
        (args.out / 'WARNINGS.md').write_text('# VALIDATION FAILED — DO NOT USE AS PASSED EVIDENCE\n\n' + repr(failure) + '\n\nInspect the retained process logs and verification.json, correct the named cause, and rerun to a new directory.\n')
        raise


if __name__ == '__main__':
    raise SystemExit(main())
