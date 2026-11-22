#!/usr/bin/env python3
"""Freeze exact and randomized synthetic original PCA reports for strict parser tests."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--exact', required=True, type=Path)
    parser.add_argument('--approx', required=True, type=Path)
    parser.add_argument('--exact-math', required=True, type=Path)
    parser.add_argument('--approx-math', required=True, type=Path)
    parser.add_argument('--out', required=True, type=Path)
    args = parser.parse_args()
    exact = json.loads((args.exact / 'verification.json').read_text())
    approx = json.loads((args.approx / 'verification.json').read_text())
    assert json.loads((args.exact_math / 'verification.json').read_text())['status'] == 'passed-independent-original-PCA-math;Java-adapter-pending'
    assert approx['status'] == 'passed-original-approximate-PCA-explicit-seed-repeatability;accuracy-and-adapter-pending'
    assert json.loads((args.approx_math / 'verification.json').read_text())['status'] == 'passed-original-randomized-PCA-independent-approximation-diagnostics'
    args.out.mkdir(parents=True, exist_ok=False)
    inputs = args.exact / 'input'
    for name, digest in exact['input_sha256'].items():
        assert sha(inputs / name) == digest
    selected = set((inputs / 'variants.extract').read_text().splitlines())
    bim = [line.split() for line in (inputs / 'known.bim').read_text().splitlines()]
    (args.out / 'markers.tsv').write_text('id\tchromosome\ta1\ta2\n' + '\n'.join('\t'.join([row[1], row[0], row[4], row[5]]) for row in bim if row[1] in selected) + '\n')
    shutil.copy2(inputs / 'all.keep', args.out / 'samples.keep')
    cases = ['case\tengine\talgorithm\tfrequency_population\tmissingness\tseed']
    retained = []
    for source, steps in [(args.exact, exact['steps']), (args.approx, approx['steps'])]:
        for step in steps:
            if source == args.exact:
                if not step['label'].startswith('pca-') or step['exit_code']:
                    continue
                label = step['engine'] + '-' + step['label']
                engine, algorithm, missing, seed = step['engine'], 'exact', step['missingness'], ''
            else:
                if 'repeat' in step['label']:
                    continue
                label = step['label']
                engine, algorithm, missing, seed = 'plink2', 'approx', 'mean', str(step['seed'])
            folder = source / label
            suffixes = ['eigenval', 'eigenvec', 'eigenvec.var' if engine == 'plink1' else 'eigenvec.allele']
            for suffix in suffixes:
                path = folder / ('result.' + suffix)
                assert sha(path) == step['files_sha256'][path.name]
                shutil.copy2(path, args.out / (label + '.' + suffix))
            cases.append('\t'.join([label, engine, algorithm, step['frequency_population'], missing, seed]))
            retained.append(step)
    assert len(retained) == 10
    (args.out / 'cases.tsv').write_text('\n'.join(cases) + '\n')
    provenance = dict(scope='Original synthetic PCA report parsing, decimal precision, exact identities and original allele-weight orientation only. Approximation accuracy is separate from format validation. No executables or patient data.',
        runtime_profile=exact['runtime_profile'], producer_sha256=sha(__file__),
        source_proofs_sha256={str(folder): sha(folder / 'verification.json') for folder in [args.exact, args.approx, args.exact_math, args.approx_math]},
        original_cases=retained, files_sha256={path.name: sha(path) for path in sorted(args.out.iterdir())})
    (args.out / 'provenance.json').write_text(json.dumps(provenance, indent=2) + '\n')
    print(json.dumps(dict(cases=10, files=len(list(args.out.iterdir())))))


if __name__ == '__main__':
    main()
