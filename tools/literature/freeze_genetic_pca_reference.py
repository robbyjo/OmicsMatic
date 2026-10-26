#!/usr/bin/env python3
"""Freeze synthetic original PCA output and independently decode its genotype panel."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--reference', required=True, type=Path)
    parser.add_argument('--math', required=True, type=Path)
    parser.add_argument('--out', required=True, type=Path)
    args = parser.parse_args()
    original = json.loads((args.reference / 'verification.json').read_text())
    proof = json.loads((args.math / 'verification.json').read_text())
    assert proof['status'] == 'passed-independent-original-PCA-math;Java-adapter-pending'
    assert len(proof['successful_cases']) == 6
    inputs = args.reference / 'input'
    for name, digest in original['input_sha256'].items():
        assert sha(inputs / name) == digest, name
    args.out.mkdir(parents=True, exist_ok=False)
    fam = [line.split() for line in (inputs / 'known.fam').read_text().splitlines()]
    bim = [line.split() for line in (inputs / 'known.bim').read_text().splitlines()]
    selected = [line.split() for line in (inputs / 'all.keep').read_text().splitlines()]
    founders = {tuple(line.split()) for line in (inputs / 'founders.keep').read_text().splitlines()}
    index = {tuple(row[:2]): i for i, row in enumerate(fam)}
    sample_indices = [index[tuple(row)] for row in selected]
    ids = set((inputs / 'variants.extract').read_text().splitlines())
    variants = [i for i, row in enumerate(bim) if row[1] in ids]
    packed = (inputs / 'known.bed').read_bytes()
    assert packed[:3] == bytes([108, 27, 1])
    stride = (len(fam) + 3) // 4
    calls = [[{0: '2', 1: 'NA', 2: '1', 3: '0'}[
        (packed[3 + v * stride + i // 4] >> (2 * (i % 4))) & 3]
        for i in sample_indices] for v in variants]
    (args.out / 'samples.tsv').write_text('fid\tiid\tfrequency_founder\n' + '\n'.join(
        '\t'.join(row + [str(int(tuple(row) in founders))]) for row in selected) + '\n')
    (args.out / 'markers.tsv').write_text('id\tcounted_allele\tother_allele\n' + '\n'.join(
        '\t'.join([bim[v][1], bim[v][4], bim[v][5]]) for v in variants) + '\n')
    (args.out / 'dosages.tsv').write_text('\n'.join('\t'.join(row) for row in calls) + '\n')
    cases = ['case\tengine\tfrequency_population\tmissingness']
    for step in original['steps']:
        if 'pca-' not in step['label'] or step['exit_code']:
            continue
        label = step['engine'] + '-' + step['label']
        folder = args.reference / label
        for suffix in ['rel', 'eigenval', 'eigenvec']:
            source = folder / ('result.' + suffix)
            assert sha(source) == step['files_sha256'][source.name]
            shutil.copy2(source, args.out / (label + '.' + suffix))
        cases.append('\t'.join([label, step['engine'], step['frequency_population'], step['missingness']]))
    (args.out / 'cases.tsv').write_text('\n'.join(cases) + '\n')
    provenance = dict(scope='Synthetic explicit-AF genetic GRM/PCA references only; no patient data, biological ancestry labels, original-program redistribution or whole-PLINK parity claim.',
        runtime_profile=original['runtime_profile'], samples=len(selected), variants=len(variants),
        cases=6, original_proof_sha256=sha(args.reference / 'verification.json'),
        independent_math_sha256=sha(args.math / 'verification.json'), producer_sha256=sha(__file__),
        original_cases=[s for s in original['steps'] if 'pca-' in s['label'] and not s['exit_code']],
        files_sha256={f.name: sha(f) for f in sorted(args.out.iterdir())})
    (args.out / 'provenance.json').write_text(json.dumps(provenance, indent=2) + '\n')
    print(json.dumps(dict(files=len(list(args.out.iterdir())), samples=len(selected), variants=len(variants), cases=6)))


if __name__ == '__main__':
    main()
