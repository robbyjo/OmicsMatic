# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Create synthetic ortholog-conversion and explicit covariate controls using original scDRS mappings."""
import argparse
import json
import sys
from pathlib import Path


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--base', required=True, type=Path)
    p.add_argument('--out', required=True, type=Path)
    a = p.parse_args()
    assert not a.out.exists()
    a.out.mkdir(parents=True)
    (a.out / 'helper-at-attempt.py').write_bytes(Path(__file__).read_bytes())
    source = a.base / 'source-intake-1/1.0.2/source'
    sys.path.insert(0, str(source))
    import anndata
    import numpy as np
    import pandas as pd
    import scdrs
    assert Path(scdrs.__file__).resolve().is_relative_to(source.resolve())
    original = a.base / 'original-cli-foundation-2/version-1.0.2'
    ad = anndata.read_h5ad(original / 'synthetic.input.h5ad')
    mapping = scdrs.util.load_homolog_mapping('human', 'mouse')
    selected, seen = [], set()
    for human, mouse in mapping.items():
        if isinstance(human, str) and isinstance(mouse, str) and mouse not in seen:
            selected.append((human, mouse))
            seen.add(mouse)
            if len(selected) == ad.n_vars: break
    assert len(selected) == ad.n_vars
    ad.var_names = [mouse for human, mouse in selected]
    ad.write_h5ad(a.out / 'mouse-expression.h5ad')
    weights = np.random.RandomState(101).uniform(.3, 2, size=60)
    genes = [selected[i][0] for i in range(3, 603, 10)]
    pd.DataFrame(dict(TRAIT=['trait-cross-species'], GENESET=[','.join(g + ':' + repr(float(w)) for g, w in zip(genes, weights))])).to_csv(a.out / 'human-gene-set.gs', sep='\t', index=False)
    cov = pd.read_csv(original / 'synthetic.cov', sep='\t', index_col=0)
    cov['sex'] = ['M' if i % 2 else 'F' for i in range(len(cov))]
    cov.to_csv(a.out / 'categorical-sex.cov', sep='\t')
    missing = cov.iloc[:86].copy()
    missing.iloc[5, missing.columns.get_loc('age')] = np.nan
    missing.to_csv(a.out / 'missing-covariates.cov', sep='\t')
    result = dict(status='PASS_SYNTHETIC_ORTHOLOG_AND_COVARIATE_CONTROLS_CREATED_ONLY',
        cells=ad.n_obs, expression_genes=ad.n_vars, selected_human_genes=len(genes),
        matched_mouse_genes=len(scdrs.util.load_gs(str(a.out / 'human-gene-set.gs'), src_species='hsapiens', dst_species='mmusculus', to_intersect=ad.var_names)['trait-cross-species'][0]),
        categorical_sex_values=['M', 'F'], missing_covariate_rows=10, explicit_missing_age_entries=1,
        original_upstream_ortholog_table_used=True, original_article_data_used=False, new_catalog_credit=False)
    assert result['matched_mouse_genes'] == 60
    (a.out / 'COMPLETED.json').write_text(json.dumps(result, indent=2) + '\n')
    print(result['status'])


if __name__ == '__main__':
    if not __debug__: raise RuntimeError('Do not disable synthetic source-mapping controls')
    main()
