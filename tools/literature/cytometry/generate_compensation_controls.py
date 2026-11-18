#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Generate complete explicit compensation/SVD references with NumPy in a NEW directory."""
import argparse
import hashlib
import json
from pathlib import Path
import numpy as np


def main():
    parser = argparse.ArgumentParser(description=__doc__); parser.add_argument('--out', required=True, type=Path)
    out = parser.parse_args().out; out.mkdir(parents=True, exist_ok=False)
    rng = np.random.default_rng(109812); rows = []
    cases = [('asymmetric2', np.array([[1, .1], [.2, 1.]])),
             ('source5', np.array([[1,.12,0,0,0],[.19,1,0,0,0],[0,0,1,.1,.04],[0,0,.2,1,.13],[0,0,.15,.08,1.]])),
             ('rectangular2x3', np.array([[.78,.13,.22],[.05,.57,.89]])),
             ('rank1', np.array([[1.,2,3],[2,4,6]])), ('zero-rectangular', np.zeros((2,3)))]
    cases += [('random'+str(n)+'x'+str(m), rng.uniform(0,1,(n,m))) for n,m in [(1,1),(1,3),(3,3),(3,6),(4,8),(6,8)]]
    for name, spectrum in cases:
        n,m = spectrum.shape; singular = np.linalg.svd(spectrum, compute_uv=False); cutoff = 1e-12
        rank = int(np.sum(singular > singular[0]*cutoff)); resolved = singular[singular > singular[0]*cutoff]
        condition = float(resolved[0]/resolved[-1]) if len(resolved) else 1
        inverse = np.linalg.pinv(spectrum, rcond=cutoff)
        for repeat in range(4):
            measured = rng.normal(0,100,m); expected = measured@inverse
            for mode in ['spectrum','inverse']:
                matrix = spectrum if mode == 'spectrum' else inverse
                vector = lambda v: ','.join(format(float(x),'.17g') for x in np.asarray(v).ravel())
                rows.append([name+'-'+str(repeat)+'-'+mode, mode, str(m), str(n), format(cutoff,'.17g'), str(rank if mode == 'spectrum' else -1),
                             format(condition,'.17g'), vector(matrix), vector(measured), vector(expected)])
    text = '# id\tmode\tdetectors\tfluorochromes\tcutoff\trank\tresolved_condition\tmatrix_row_major\tmeasured\texpected\n'+'\n'.join('\t'.join(r) for r in rows)+'\n'
    (out/'compensation-controls.tsv').write_text(text, encoding='utf8', newline='\n')
    record = dict(status='PASS_NUMPY_COMPLETE_COMPENSATION_REFERENCE_GENERATION_ONLY', numpy=np.__version__, cases=len(rows),
                  source5='FCS3.1 implementation guidance Table1, DOI10.1002/cyto.a.22018; rows signals, columns detectors.',
                  formula='measured row @ np.linalg.pinv(spectrum, rcond=explicit cutoff); already-inverted matrices are passed unchanged.',
                  fixture=dict(bytes=len(text.encode()), sha256=hashlib.sha256(text.encode()).hexdigest()),
                  generator_sha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(), inputs_truncated=False)
    (out/'COMPLETED.json').write_text(json.dumps(record, indent=2)+'\n', encoding='utf8'); print(json.dumps(record))


if __name__ == '__main__':
    main()
