"""Collect actual unmodified local Enrichr class results from one exact private edition."""
import argparse
import gzip
import hashlib
import json
from pathlib import Path
import platform
import random
import sys


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--namespace', type=Path, required=True)
    parser.add_argument('--edition', required=True, choices=['1.0.4', '1.1.2', '1.1.3', '1.1.8'])
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args()
    if args.out.exists():
        raise SystemExit('Require a new output directory')
    sys.path.insert(0, str(args.namespace.resolve()))
    import gseapy
    import gseapy.enrichr
    import numpy
    import scipy
    import pandas
    import importlib
    original = importlib.import_module('gseapy.enrichr')
    if gseapy.__version__ != args.edition:
        raise SystemExit('Wrong original edition; no substitution or installation')
    root = Path(__file__).resolve().parents[2]
    source = root / 'src/main/resources/org/jlinalg/omics/gseapy-statistics/original' / args.edition / 'gseapy'
    hashes = {}
    for name in ['enrichr.py', 'stats.py']:
        actual = args.namespace / 'gseapy' / name
        data = actual.read_bytes()
        if data.decode().replace('\r\n', '\n') != (source / name).read_text():
            raise SystemExit('Original scientific source differs: ' + name)
        hashes[name] = {'installed_sha256': hashlib.sha256(data).hexdigest(),
                        'normalized_lf_sha256': hashlib.sha256(data.decode().replace('\r\n', '\n').encode()).hexdigest()}
    cases = []
    for query in [['A', 'B'], [' a ', 'b'], ['+1', '02'], [], ['A', 'A', 'C'], ['α', 'ß']]:
        for background in [None, ['A', 'B', 'C', 'D'], 6]:
            cases.append({'query': query, 'gene_sets': {'one': ['A', 'C'], 'two': ['A', 'B'], 'zero': ['D']}, 'background': background})
    rng = random.Random(20261111)
    identifiers = ['A', 'B', 'C', 'a', 'b', 'c', '  A ', 'ß', 'α', 'Α', '01', '1', '2', ' 2 ']
    for _ in range(82):
        cases.append({'query': rng.choices(identifiers, k=rng.randrange(1, 8)),
                      'gene_sets': {'path' + str(i): rng.choices(identifiers, k=rng.randrange(7)) for i in range(rng.randrange(1, 6))},
                      'background': rng.choice([None, 0, 3, 20, identifiers, identifiers[:5]])})
    results = []
    for specification in cases:
        case = dict(specification, edition=args.edition, alpha=.05)
        try:
            model = original.Enrichr(gene_list=specification['query'], gene_sets=specification['gene_sets'],
                                     background=specification['background'], organism='human', outdir=None,
                                     cutoff=.05, no_plot=True, verbose=False)
            model.run()
            rows = []
            frame = model.results
            if hasattr(frame, 'iterrows'):
                for _, row in frame.iterrows():
                    number = lambda field: float(row[field]) if numpy.isfinite(row[field]) else str(float(row[field]))
                    rows.append({'term': row['Term'], 'overlap': row['Overlap'], 'p': number('P-value'),
                                 'q': number('Adjusted P-value'), 'odds': number('Odds Ratio'),
                                 'combined_available': 'Combined Score' in frame.columns,
                                 'combined': number('Combined Score') if 'Combined Score' in frame.columns else None,
                                 'hits': sorted(row['Genes'].split(';'))})
            case.update(success=True, rows=rows)
        except Exception as failure:
            case.update(success=False, error_type=type(failure).__name__, error=str(failure))
        results.append(case)
    args.out.mkdir(parents=True)
    payload = (json.dumps({'edition': args.edition, 'cases': results}, ensure_ascii=False, indent=2, allow_nan=False) + '\n').encode()
    (args.out / 'controls.json.gz').write_bytes(gzip.compress(payload, mtime=0))
    receipt = {'edition': args.edition, 'python': sys.version, 'platform': platform.platform(),
               'numpy': numpy.__version__, 'scipy': scipy.__version__, 'pandas': pandas.__version__,
               'scientific_sources': hashes, 'cases': len(results), 'source_successes': sum(c['success'] for c in results),
               'source_failures': sum(not c['success'] for c in results),
               'uncompressed_sha256': hashlib.sha256(payload).hexdigest(),
               'scope': 'Actual unchanged Enrichr class, one supplied local library per call, no remote resource or permutation. Source errors are retained. Bounded synthetic controls.'}
    (args.out / 'VALIDATION.json').write_text(json.dumps(receipt, indent=2) + '\n')
    print(json.dumps(receipt))


if __name__ == '__main__':
    main()
