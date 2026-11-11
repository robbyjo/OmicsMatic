# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Create explicitly synthetic scDRS workflow edge controls inside the private Linux runtime."""
import argparse
import csv
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
    from scipy import sparse
    source = a.base / 'original-cli-foundation-2/version-1.0.2'
    base = anndata.read_h5ad(source / 'synthetic.input.h5ad')
    cov = pd.read_csv(source / 'synthetic.cov', sep='\t', index_col=0)
    gs = pd.read_csv(source / 'synthetic.gs', sep='\t')
    imbalanced = base.copy()
    imbalanced.obs['celltype'] = ['major'] * 70 + ['middle'] * 20 + ['minor'] * 6
    imbalanced.write_h5ad(a.out / 'imbalanced.h5ad')
    quoted = base.copy()
    cells = list(quoted.obs_names)
    cells[:3] = ['cell"alpha', '家族', '🧬']
    quoted.obs_names = cells
    genes = list(quoted.var_names)
    old_gene = genes[3]
    genes[3] = 'gene"alpha'
    quoted.var_names = genes
    quoted.obs['cell type'] = ['type"alpha' if i % 3 == 0 else '脳' if i % 3 == 1 else '🧬' for i in range(quoted.n_obs)]
    quoted.write_h5ad(a.out / 'quoted-unicode.h5ad')
    cov.index = cells
    cov.to_csv(a.out / 'quoted-unicode.cov', sep='\t')
    quoted_gs = gs.copy()
    quoted_gs['GENESET'] = quoted_gs['GENESET'].str.replace(old_gene + ':', 'gene"alpha:', regex=False)
    quoted_gs.to_csv(a.out / 'quoted-unicode.gs', sep='\t', index=False)
    rng = np.random.RandomState(20261110)
    rates = np.exp(rng.uniform(-1, 3, size=4200))
    counts = rng.poisson(rates[None, :] * rng.lognormal(0, .3, size=(96, 1))).astype(np.float64)
    raw_matrix = np.zeros((102, 4204))
    raw_matrix[:96, :4200] = counts
    raw_matrix[96:, :20] = 1
    raw_matrix[:4, 4200:] = np.eye(4)
    obs = base.obs.copy()
    extra = pd.DataFrame(dict(age=[25.] * 6, sex=[0.] * 6, n_genes=[20] * 6,
        celltype=['filtered-low-complexity'] * 6, module=np.linspace(-1, 1, 6)), index=['filtered-cell-' + str(i) for i in range(6)])
    obs = pd.concat([obs, extra])
    raw = anndata.AnnData(sparse.csr_matrix(raw_matrix), obs=obs,
        var=pd.DataFrame(index=list(base.var_names) + ['filtered-gene-' + str(i) for i in range(4)]), dtype=np.float64)
    raw.write_h5ad(a.out / 'raw-with-filtered-identities.h5ad')
    gene_ids = ['magma-gene-%04d' % i for i in range(1100)]
    z = pd.DataFrame({'trait-Z': np.linspace(-4, 7, 1100), 'trait-A': np.cos(np.arange(1100) * .017) * 4}, index=gene_ids)
    z.index.name = 'GENE'
    z.iloc[0, 0] = 40
    z.iloc[7, 0] = np.nan
    z.iloc[9, 1] = np.nan
    z.to_csv(a.out / 'gene-z.tsv', sep='\t')
    pvalues = pd.DataFrame({'trait-Z': np.linspace(0, 1, 1100), 'trait-A': np.linspace(1, 0, 1100)}, index=gene_ids)
    pvalues.index.name = 'GENE'
    pvalues.iloc[7, 0] = np.nan
    pvalues.iloc[9, 1] = np.nan
    pvalues.to_csv(a.out / 'gene-p.tsv', sep='\t')
    multi = pd.concat([gs, gs.copy()], ignore_index=True)
    multi['TRAIT'] = ['trait-B', 'trait-A']
    multi.to_csv(a.out / 'two-traits.gs', sep='\t', index=False)
    full = pd.read_csv(source / 'original-output/trait-synthetic.full_score.gz', sep='\t', index_col=0)
    full.iloc[:80].to_csv(a.out / 'trait-partial.full_score.gz', sep='\t', compression='gzip')
    full_bad = full.copy()
    full_bad.iloc[3, 1] = np.nan
    full_bad.to_csv(a.out / 'trait-nonfinite.full_score.gz', sep='\t', compression='gzip')
    duplicate = base.copy()
    names = list(duplicate.obs_names)
    names[1] = names[0]
    duplicate.obs_names = names
    duplicate.write_h5ad(a.out / 'duplicate-cells.h5ad')
    with (a.out / 'too-few-overlapping.gs').open('x', encoding='utf-8', newline='') as stream:
        writer = csv.writer(stream, delimiter='\t', lineterminator='\n')
        writer.writerow(['TRAIT', 'GENESET'])
        writer.writerow(['trait-small', ','.join(base.var_names[:5])])
    source_cov = pd.read_csv(source / 'synthetic.cov', sep='\t', index_col=0)
    source_cov['sex'] = ['M' if i % 2 else 'F' for i in range(96)]
    source_cov.to_csv(a.out / 'categorical-sex.cov', sep='\t')
    result = dict(status='PASS_SYNTHETIC_SCDRS_WORKFLOW_CONTROLS_CREATED_ONLY', source_fixture_cells=96,
        source_fixture_genes=4200, raw_cells=102, raw_genes=4204, partial_score_cells=80,
        numeric_gene_statistic_genes=1100, variants=['imbalance', 'quoted-and-unicode-identities', 'raw-source-filter',
        'two-traits', 'missing-gene-statistics-and-P-endpoints', 'literal-cell-intersection', 'nonfinite-score-refusal',
        'duplicate-cell-refusal', 'source-small-gene-set-skip', 'historical-categorical-covariate-refusal'],
        original_article_data_used=False, new_catalog_credit=False)
    (a.out / 'COMPLETED.json').write_text(json.dumps(result, indent=2) + '\n')
    print(result['status'])


if __name__ == '__main__':
    if not __debug__: raise RuntimeError('Do not disable synthetic-control checks')
    main()
