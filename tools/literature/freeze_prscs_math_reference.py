#!/usr/bin/env python3
"""Freeze synthetic original PRS-CS allele, marginal and posterior-unit references."""
import argparse
import hashlib
import json
from pathlib import Path


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--math', type=Path, required=True)
    parser.add_argument('--original', type=Path, required=True)
    parser.add_argument('--checks', type=Path, required=True)
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args()
    math = json.loads((args.math/'verification.json').read_text())
    original = json.loads((args.original/'verification.json').read_text())
    checks = json.loads((args.checks/'verification.json').read_text())
    assert math['status'] == 'passed-original-three-profile-allele-and-marginal-arithmetic-reference-capture'
    assert original['status'].startswith('passed-original-three-profile')
    assert checks['status'] == 'passed-independent-original-identity-family-scaling-and-draw-mean-contracts'
    assert len(math['alignment_cases']) == 1728 and len(math['transform_cases']) == 592
    assert len(original['steps']) == 56 and len(checks['scale_rows']) == 1040
    assert checks['source_sha256'] == sha(args.original/'verification.json')
    for run in math['runs']:
        folder = args.math/(run['label']+'-'+run['kind'])
        for name, digest in run['files_sha256'].items():
            assert sha(folder/name) == digest
    for run in original['steps']:
        for name, digest in run['files_sha256'].items():
            assert sha(args.original/run['label']/name) == digest
    args.out.mkdir(parents=True, exist_ok=False)
    with (args.out/'alignment.tsv').open('w', encoding='utf-8', newline='') as stream:
        stream.write('reference\ttarget\tsummary\treference_frequency\tmatched\tsummary_sign\treference_sign\teffect_frequency\toriginal_marginal\n')
        for row in math['alignment_cases']:
            values = list(row['original'].values())
            assert len(values) == 3 and all(v == values[0] for v in values)
            value = values[0]
            fields = [row['reference'], row['target'], row['summary'], repr(row['reference_frequency']), str(value is not None).lower()]
            fields += ['0', '0', '', ''] if value is None else [str(1 if value['BETA'] > 0 else -1), str(value['FLP']), repr(value['MAF']), repr(value['BETA'])]
            stream.write('\t'.join(fields)+'\n')
    with (args.out/'transforms.tsv').open('w', encoding='utf-8', newline='') as stream:
        stream.write('profile\tformat\teffect\tuncertainty\tsign\tn\toriginal_standardized_marginal\n')
        for row in math['transform_cases']:
            stream.write('\t'.join(str(row[k]) for k in ['profile','format','effect','uncertainty','sign','n','original_standardized_marginal'])+'\n')
    with (args.out/'posterior-scaling.tsv').open('w', encoding='utf-8', newline='') as stream:
        keys = ['profile','format','shrinkage','output','chromosome','snp','column','frequency','standardized','per_allele']
        stream.write('\t'.join(keys)+'\n')
        for row in checks['scale_rows']:
            stream.write('\t'.join(str(row[k]) for k in keys)+'\n')
    record = dict(scope='Synthetic actual-original function returns and original MCMC unit relationships. 1728 allele triples (144 matched),592 marginal transforms and1040 rounded posterior-scaling cases. Java arithmetic validation does not fit the Bayesian model or establish biological calibration,convergence,historical cohort or complete-tool parity.',
                  producer_sha256=sha(__file__), original_math_proof_sha256=sha(args.math/'verification.json'),
                  original_mcmc_proof_sha256=sha(args.original/'verification.json'), independent_checks_sha256=sha(args.checks/'verification.json'),
                  original_source_files_sha256=original['source_files_sha256'],
                  files_sha256={p.name: sha(p) for p in sorted(args.out.iterdir()) if p.is_file()},
                  runtime_profiles={'source-fbc9ec0': 'CPython3.12.14,numpy1.26.4,scipy1.11.4,h5py3.11.0',
                                    '1.0.0-and-1.1.0': 'CPython3.10.11,numpy1.23.5,scipy1.8.1,h5py3.8.0;EOLruntime,historicaloffline reproduction only'})
    (args.out/'provenance.json').write_text(json.dumps(record, indent=2)+'\n')
    (args.out/'SHA256SUMS').write_text(''.join(sha(p)+'  '+p.name+'\n' for p in sorted(args.out.iterdir()) if p.is_file()))
    print(json.dumps(dict(status='passed', alignment_cases=1728, transform_cases=592, scaling_cases=1040)))


if __name__ == '__main__':
    main()
