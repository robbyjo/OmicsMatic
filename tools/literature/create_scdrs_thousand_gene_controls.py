# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Create an explicit synthetic1000-gene weighted set matching the paper-sized scoring role."""
import argparse
import json
from pathlib import Path


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--base', required=True, type=Path)
    p.add_argument('--out', required=True, type=Path)
    a = p.parse_args()
    assert not a.out.exists()
    a.out.mkdir(parents=True)
    (a.out / 'helper-at-attempt.py').write_bytes(Path(__file__).read_bytes())
    import anndata
    import numpy as np
    import pandas as pd
    data = anndata.read_h5ad(a.base / 'original-cli-foundation-2/version-1.0.2/synthetic.input.h5ad', backed='r')
    genes = list(data.var_names[:1000])
    data.file.close()
    weights = np.linspace(5, .1, 1000)
    pd.DataFrame(dict(TRAIT=['trait-thousand-genes'], GENESET=[','.join(g + ':' + repr(float(w)) for g, w in zip(genes, weights))])).to_csv(a.out / 'thousand-genes.gs', sep='\t', index=False)
    result = dict(status='PASS_SYNTHETIC1000_GENE_WEIGHT_CONTROL_CREATED_ONLY', genes=1000,
        weights_explicit=True, original_article_data_used=False, biological_atlas_analyzed=False, new_catalog_credit=False)
    (a.out / 'COMPLETED.json').write_text(json.dumps(result, indent=2) + '\n')
    print(result['status'])


if __name__ == '__main__':
    if not __debug__: raise RuntimeError('Do not disable synthetic gene-set controls')
    main()
